/**
 * Matomo tracker bootstrap (Wave-6 Task A).
 *
 * The tracker is driven ENTIRELY by runtime config from `GET /auth/site-config`
 * (see litemall.tracking.matomo.* on the edge). Unconfigured ⇒ `initMatomo`
 * resolves to "disabled": no script tag is injected, no `_paq` global is
 * created, buffered page views are dropped — byte-identical behavior to the
 * pre-Wave-6 bundle. Do-Not-Track is honored the same way (hard disable).
 *
 * Page views fired before the async config fetch resolves are buffered here
 * (NOT in `_paq`: matomo.js replays `_paq` strictly in order, so config
 * entries must precede any `trackPageView`). `initMatomo` flushes the buffer
 * after pushing the tracker config.
 *
 * Campaign attribution (`utm_source`/`utm_medium`/`utm_campaign`, shared
 * convention with the affiliate `?invite=` links — see
 * app/shared/util/campaignLink.ts) needs no code here: Matomo parses utm_*
 * from the tracked URL natively, which is why `trackPageView` reports the full
 * URL including the query string (except on auth pages, see MatomoTracker).
 */

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

type TrackerState = 'pending' | 'enabled' | 'disabled';

let state: TrackerState = 'pending';
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
 * Decide once whether tracking is on. Idempotent; safe to call with the
 * fetch-failure fallback (`{matomoUrl: null, matomoSiteId: null}`).
 */
export const initMatomo = (cfg: SiteTrackingConfig): void => {
  if (state !== 'pending') return;
  if (!cfg.matomoUrl || !cfg.matomoSiteId || doNotTrack()) {
    state = 'disabled';
    pendingViews = [];
    return;
  }
  const base = cfg.matomoUrl.replace(/\/+$/, '');
  goodsDimension = cfg.matomoGoodsDimension ?? 1;

  const paq = (window._paq = window._paq ?? []);
  paq.push(['setTrackerUrl', `${base}/matomo.php`]);
  paq.push(['setSiteId', String(cfg.matomoSiteId)]);
  paq.push(['enableLinkTracking']);
  state = 'enabled';

  const queued = pendingViews;
  pendingViews = [];
  queued.forEach(v => trackPageView(v.url, v.title, v.goodsId));

  const script = document.createElement('script');
  script.async = true;
  script.src = `${base}/matomo.js`;
  document.head.appendChild(script);
};

/**
 * Track one SPA page view. `goodsId` (product-detail routes) rides along as
 * the configured custom dimension; non-product views delete the dimension so
 * a goods id never leaks onto the next page view.
 */
export const trackPageView = (url: string, title: string, goodsId?: string): void => {
  if (state === 'disabled') return;
  if (state === 'pending') {
    pendingViews.push({ url, title, goodsId });
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
