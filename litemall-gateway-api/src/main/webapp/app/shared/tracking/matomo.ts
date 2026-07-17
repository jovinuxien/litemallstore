/**
 * Matomo tracker bootstrap (Wave-6 Task A) with opt-in consent (Wave-7 Task C).
 *
 * The tracker is driven ENTIRELY by runtime config from `GET /auth/site-config`
 * (see litemall.tracking.matomo.* on the edge). Unconfigured ⇒ `initMatomo`
 * resolves to "unavailable": no script tag is injected, no `_paq` global is
 * created, buffered page views are dropped — byte-identical behavior to the
 * pre-Wave-6 bundle. Do-Not-Track is honored the same way (hard disable).
 *
 * <p><b>Consent (Wave-7).</b> Being configured is no longer sufficient: nothing is
 * injected and no request is made until the visitor affirmatively accepts. The
 * script tag itself is withheld rather than merely muted, because loading
 * matomo.js IS the network call we are promising not to make — `requireConsent`
 * alone would still contact the server. `requireConsent`/`setConsentGiven` are
 * pushed anyway (belt and braces: they are what stop matomo.js writing cookies if
 * it is ever loaded by another path), and `forgetConsentGiven` on withdrawal is
 * what actually deletes cookies already dropped.
 *
 * <p>Precedence is deliberate and one-directional: unconfigured or DNT wins over
 * any stored consent. A visitor who accepted last month and has since turned on
 * DNT is not tracked, and is not asked.
 *
 * Page views fired before the async config fetch resolves are buffered here
 * (NOT in `_paq`: matomo.js replays `_paq` strictly in order, so config entries
 * must precede any `trackPageView`). While awaiting a consent decision the buffer
 * holds only the LATEST view — accepting then counts the page you are on, rather
 * than retroactively reporting everywhere you went while ignoring the banner.
 *
 * Campaign attribution (`utm_source`/`utm_medium`/`utm_campaign`, shared
 * convention with the affiliate `?invite=` links — see
 * app/shared/util/campaignLink.ts) needs no code here: Matomo parses utm_* from
 * the tracked URL natively, which is why `trackPageView` reports the full URL
 * including the query string (except on auth pages, see MatomoTracker).
 */

import { ConsentChoice, consentSnapshot, setTrackingReason } from 'app/shared/tracking/consent';

declare global {
  interface Window {
    _paq?: unknown[][];
  }
}

export interface SiteTrackingConfig {
  matomoUrl: string | null;
  matomoSiteId: string | null;
  /** Custom-dimension index carrying the goods id on product-detail views. */
  matomoGoodsDimension?: number;
}

type TrackerState =
  /** Site config not resolved yet. */
  | 'pending'
  /** Unconfigured or DNT — permanent for this page load; consent cannot revive it. */
  | 'unavailable'
  /** Configured, no decision yet — the banner is showing. */
  | 'awaiting-consent'
  /** Consented; script injected and views flowing. */
  | 'granted'
  /** Actively refused. */
  | 'denied';

let state: TrackerState = 'pending';
let config: SiteTrackingConfig | null = null;
let scriptInjected = false;
let goodsDimension = 1;
let lastUrl: string | null = null;
let pendingViews: { url: string; title: string; goodsId?: string }[] = [];

const doNotTrack = (): boolean => {
  const dnt =
    navigator.doNotTrack ?? (window as unknown as { doNotTrack?: string }).doNotTrack;
  return dnt === '1' || dnt === 'yes';
};

/** Visible for tests/debugging only. */
export const matomoState = (): TrackerState => state;

/**
 * Decide once whether tracking is even possible. Idempotent; safe to call with the
 * fetch-failure fallback (`{matomoUrl: null, matomoSiteId: null}`).
 */
export const initMatomo = (cfg: SiteTrackingConfig): void => {
  if (state !== 'pending') return;
  if (!cfg.matomoUrl || !cfg.matomoSiteId || doNotTrack()) {
    state = 'unavailable';
    pendingViews = [];
    setTrackingReason(!cfg.matomoUrl || !cfg.matomoSiteId ? 'unconfigured' : 'dnt');
    return;
  }
  config = cfg;
  goodsDimension = cfg.matomoGoodsDimension ?? 1;
  state = 'awaiting-consent';
  setTrackingReason('available');
  applyConsent(consentSnapshot().choice);
};

/**
 * Apply the visitor's decision. Called on init with any stored choice and again on
 * every change. A no-op while unconfigured or under DNT — those outrank consent.
 */
export const applyConsent = (choice: ConsentChoice | null): void => {
  if (state === 'pending' || state === 'unavailable') return;
  if (choice === 'granted') grant();
  else if (choice === 'denied') deny();
  else revokeToAwaiting();
};

const grant = (): void => {
  if (state === 'granted') return;
  state = 'granted';
  const base = config!.matomoUrl!.replace(/\/+$/, '');
  const paq = (window._paq = window._paq ?? []);

  if (!scriptInjected) {
    paq.push(['requireConsent']);
    paq.push(['setTrackerUrl', `${base}/matomo.php`]);
    paq.push(['setSiteId', String(config!.matomoSiteId)]);
    paq.push(['enableLinkTracking']);
  }
  paq.push(['setConsentGiven']);

  const queued = pendingViews;
  pendingViews = [];
  queued.forEach(v => trackPageView(v.url, v.title, v.goodsId));

  if (!scriptInjected) {
    scriptInjected = true;
    const script = document.createElement('script');
    script.async = true;
    script.src = `${base}/matomo.js`;
    document.head.appendChild(script);
  }
};

const deny = (): void => {
  state = 'denied';
  forget();
};

/**
 * Enter (or return to) the un-decided state.
 *
 * <p>The buffer survives arriving here from `pending` — that is the normal path on
 * every page load, and the view it holds is the page the visitor is looking at while
 * the banner asks. Dropping it would mean accepting tracks nothing until the next
 * navigation. Coming back from `granted` (a withdrawal) is the opposite case: stop,
 * and forget what was collected. Trimming to the newest view keeps the promise in the
 * file header — accepting reports where you are, not everywhere you have been.
 */
const revokeToAwaiting = (): void => {
  const wasGranted = state === 'granted';
  state = 'awaiting-consent';
  if (wasGranted) forget();
  else pendingViews = pendingViews.slice(-1);
};

/**
 * Stop tracking and drop anything already stored. Harmless before the script loads
 * (`_paq` simply replays it when matomo.js arrives, which it never will unless
 * consent is granted).
 */
const forget = (): void => {
  pendingViews = [];
  lastUrl = null;
  if (window._paq) window._paq.push(['forgetConsentGiven']);
};

/**
 * Track one SPA page view. `goodsId` (product-detail routes) rides along as the
 * configured custom dimension; non-product views delete the dimension so a goods id
 * never leaks onto the next page view.
 */
export const trackPageView = (url: string, title: string, goodsId?: string): void => {
  if (state === 'unavailable' || state === 'denied') return;
  if (state === 'pending') {
    pendingViews.push({ url, title, goodsId });
    return;
  }
  if (state === 'awaiting-consent') {
    // Only the latest — see the consent note in the file header.
    pendingViews = [{ url, title, goodsId }];
    return;
  }
  const paq = window._paq!;
  if (lastUrl) paq.push(['setReferrerUrl', lastUrl]);
  paq.push(['setCustomUrl', url]);
  paq.push(['setDocumentTitle', title]);
  if (goodsId) {
    paq.push(['setCustomDimension', goodsDimension, goodsId]);
  } else {
    paq.push(['deleteCustomDimension', goodsDimension]);
  }
  paq.push(['trackPageView']);
  lastUrl = url;
};
