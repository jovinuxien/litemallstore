/**
 * First-party behavioral emitter (behavioral targeting Phase 0 — contract:
 * doc/behavioral-events.md). Batches intent events to POST /srv/track/collect.
 *
 * Identity is EDGE-OWNED: this module never generates or sends visitor/session
 * ids — it only mirrors the consent choice into the `lm_consent` cookie, which
 * tells the gateway to mint HttpOnly `lm_vid`/`lm_sid` cookies and forward them
 * as headers. STRICT prior consent (user decision 2026-08-04): nothing is
 * buffered or sent, for any region, unless the stored choice is `granted`.
 * DNT wins over a stored grant, matching matomo.ts.
 *
 * Transport: buffer flushed every 5 s or at 20 events via axios (carries the
 * Bearer when logged in, which is what lets the backend stitch visitor→user);
 * on pagehide/hidden the tail flushes via navigator.sendBeacon. Everything is
 * wrapped so tracking can never break the storefront: any failure drops events
 * silently.
 */

import baseAxios from 'app/config/axiosinstance';
import { consentSnapshot, setTrackingReason, subscribeConsent } from 'app/shared/tracking/consent';
import { goodsIdFromRoute } from 'app/shared/util/slug';

type FpEventType =
  | 'page_view'
  | 'view_item'
  | 'view_category'
  | 'search'
  | 'click_result'
  | 'add_to_cart'
  | 'remove_from_cart'
  | 'begin_checkout';

interface FpEvent {
  eventId: string;
  type: FpEventType;
  occurredAt: number;
  goodsId?: number;
  productId?: number;
  categoryId?: number;
  searchQuery?: string;
  position?: number;
  pageType?: string;
  payload?: Record<string, unknown>;
}

const COLLECT_URL = '/srv/track/collect';
const CONSENT_URL = '/srv/track/consent';
const CONSENT_COOKIE = 'lm_consent';
const CONSENT_COOKIE_MAX_AGE = 34128000; // 13 months, mirrors lm_vid
const FLUSH_MS = 5000;
const FLUSH_AT = 20;

let buffer: FpEvent[] = [];
let timer: ReturnType<typeof setTimeout> | undefined;
let started = false;
let lastMirroredChoice: string | undefined;

const hasDnt = (): boolean => {
  try {
    return navigator.doNotTrack === '1' || (window as any).doNotTrack === '1';
  } catch {
    return false;
  }
};

const granted = (): boolean => consentSnapshot().choice === 'granted' && !hasDnt();

const uuid = (): string | undefined => {
  try {
    return crypto.randomUUID();
  } catch {
    return undefined; // ancient/locked-down browser: skip the event, never throw
  }
};

const send = (events: FpEvent[], viaBeacon: boolean): void => {
  if (!events.length) return;
  try {
    if (viaBeacon && typeof navigator.sendBeacon === 'function') {
      // Same-origin Blob POST — the only delivery that survives navigation away.
      navigator.sendBeacon(COLLECT_URL, new Blob([JSON.stringify({ events })], { type: 'application/json' }));
      return;
    }
    void baseAxios.post(COLLECT_URL, { events }).catch(() => undefined);
  } catch {
    // fail silent by contract
  }
};

const flush = (viaBeacon = false): void => {
  if (timer) {
    clearTimeout(timer);
    timer = undefined;
  }
  const events = buffer;
  buffer = [];
  send(events, viaBeacon);
};

/** Queue one event. No-op unless consent is granted (strict prior consent). */
export const fpTrack = (type: FpEventType, fields: Omit<FpEvent, 'eventId' | 'type' | 'occurredAt'> = {}): void => {
  try {
    if (!granted()) return;
    const eventId = uuid();
    if (!eventId) return;
    buffer.push({ eventId, type, occurredAt: Date.now(), ...fields });
    if (buffer.length >= FLUSH_AT) {
      flush();
    } else if (!timer) {
      timer = setTimeout(() => flush(), FLUSH_MS);
    }
  } catch {
    // fail silent by contract
  }
};

const CATEGORY_PATH = /^\/category\/(\d+)/;
const PRODUCT_PATH = /^\/product\/([^/]+)$/;

const pageTypeOf = (pathname: string): string => {
  if (pathname === '/') return 'home';
  if (pathname.startsWith('/product/')) return 'pdp';
  if (pathname.startsWith('/category/')) return 'category';
  if (pathname === '/search') return 'search';
  if (pathname === '/cart') return 'cart';
  if (pathname.startsWith('/checkout')) return 'checkout';
  if (pathname === '/deals') return 'deals';
  return 'other';
};

/**
 * One call per route change (from MatomoTracker's location effect — the single
 * per-navigation hook). Emits page_view always, plus the route-derived intent
 * events: view_category on the category landing, search when /search carries a
 * query. PATH only in the payload — never the query string (tokens/invite
 * codes must not enter the event log; the search query is whitelisted
 * separately into its own typed field).
 */
export const fpTrackRoute = (pathname: string, search: string): void => {
  try {
    fpTrack('page_view', { pageType: pageTypeOf(pathname), payload: { path: pathname } });
    const category = CATEGORY_PATH.exec(pathname);
    if (category) {
      const categoryId = Number(category[1]);
      if (Number.isFinite(categoryId) && categoryId > 0) fpTrack('view_category', { categoryId });
    }
    if (pathname === '/search') {
      const q = new URLSearchParams(search).get('q');
      if (q && q.trim()) fpTrack('search', { searchQuery: q.trim().slice(0, 255) });
    }
    // view_item is emitted by the ecommerce facade (it carries price facts the
    // route alone does not); PDP navigation only produces the page_view here.
  } catch {
    // fail silent by contract
  }
};

/** Numeric goods id or undefined — hits/routes may carry `cj_<pid>` keys. */
export const numericGoodsId = (value: unknown): number | undefined => {
  const n = typeof value === 'string' ? Number(goodsIdFromRoute(value) ?? value) : Number(value);
  return Number.isFinite(n) && n > 0 ? Math.trunc(n) : undefined;
};

const mirrorConsentCookie = (choice: string): void => {
  try {
    const secure = window.location.protocol === 'https:' ? '; Secure' : '';
    document.cookie = `${CONSENT_COOKIE}=${choice}; Path=/; Max-Age=${CONSENT_COOKIE_MAX_AGE}; SameSite=Lax${secure}`;
  } catch {
    // fail silent
  }
};

const recordConsent = (choice: string): void => {
  // Cookie FIRST: the edge mints lm_vid on any request carrying a granted
  // lm_consent, so this very POST becomes attributable (contract §identity).
  mirrorConsentCookie(choice);
  try {
    void baseAxios
      .post(CONSENT_URL, { choice, scope: 'analytics', occurredAt: Date.now() })
      .catch(() => undefined);
  } catch {
    // fail silent
  }
};

/**
 * Mount once (MatomoTracker does it). Mirrors the stored consent into the
 * cookie (returning visitors whose grant predates this feature get identity on
 * their first navigation), relays every later choice change, and installs the
 * leave-page beacon flush.
 */
export const initFirstPartyTracking = (): void => {
  if (started) return;
  started = true;
  try {
    // First-party tracking needs no configuration, so there is ALWAYS a real
    // consent choice to make — upgrade the banner's reason to 'available'
    // (same only-upgrade stance as metaPixel; DNT keeps outranking, and
    // initMatomo has already rendered its dnt/unconfigured verdict because we
    // are called after it in MatomoTracker's site-config chain). Without this
    // the banner never shows on a deployment with Matomo/Pixel unconfigured,
    // and strict prior consent would silently mean "no data, ever".
    if (!hasDnt()) setTrackingReason('available');
    const stored = consentSnapshot().choice;
    if (stored === 'granted' && !hasDnt()) {
      mirrorConsentCookie('granted');
      lastMirroredChoice = 'granted';
    }
    subscribeConsent(() => {
      const { choice } = consentSnapshot();
      if (!choice || choice === lastMirroredChoice) return;
      lastMirroredChoice = choice;
      if (choice === 'denied') buffer = [];
      recordConsent(choice);
    });
    const onLeave = (): void => flush(true);
    window.addEventListener('pagehide', onLeave);
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') onLeave();
    });
  } catch {
    // fail silent by contract
  }
};
