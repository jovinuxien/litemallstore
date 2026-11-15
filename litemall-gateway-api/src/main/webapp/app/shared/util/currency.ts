import { useSyncExternalStore } from 'react';

/**
 * Display-currency store (stage A — docs/spec-display-currency.md).
 *
 * THE STORE CHARGES EUR. This module only answers "which currency do we WRITE prices
 * in for this visitor", and holds the ECB reference rates used to convert the euro
 * amounts the server sends. Nothing here reaches the money path: order totals,
 * Stripe, refunds, mails and order history stay in euros (`moneyCharged`).
 *
 * Same tiny observable shape as `i18n/locale.ts` and `shared/config/siteConfig.ts`,
 * and not Redux for the same reason: `money.ts` reads it outside React.
 *
 * RESOLUTION ORDER (first hit that is ENABLED wins): `?currency=` query → cookie
 * `lm_cur` → the edge's suggestion for the visitor's country (Cloudflare's
 * CF-IPCountry: SE→SEK, DK→DKK, NO→NOK, GB→GBP) → EUR.
 *
 * ENABLED currencies and rates come from `GET /auth/currency` (env
 * `LITEMALL_CURRENCY_DISPLAY`, default `EUR` = feature off). A currency is enabled
 * only when the edge holds a fresh ECB rate for it, so "enabled" always implies "we
 * can convert". Fetch failure, an old edge, or stale rates all resolve to EUR-only:
 * the visitor then sees exactly the euro prices that are charged — never a stale or
 * invented conversion.
 *
 * The last answer is kept in localStorage for a day so a returning visitor's FIRST
 * paint is already in their currency; the live answer replaces it as soon as it
 * arrives. Storage is a convenience only — every access is guarded.
 */

export const SUPPORTED_CURRENCIES = ['EUR', 'SEK', 'DKK', 'NOK', 'GBP'] as const;
export type Currency = (typeof SUPPORTED_CURRENCIES)[number];
export const CHARGE_CURRENCY: Currency = 'EUR';

export const CURRENCY_COOKIE = 'lm_cur';
export const CURRENCY_QUERY_PARAM = 'currency';
const CACHE_KEY = 'lm_cur_cache';
const CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export const isCurrency = (v: unknown): v is Currency => typeof v === 'string' && (SUPPORTED_CURRENCIES as readonly string[]).includes(v);

export interface CurrencyState {
  /** Currency prices are written in right now. Always a member of `enabled`. */
  currency: Currency;
  /** Currencies on offer; always starts with EUR. Length 1 ⇒ feature off, no selector. */
  enabled: Currency[];
  /** Units per ONE euro (ECB reference rate) for each enabled non-euro currency. */
  rates: Partial<Record<Currency, number>>;
  /** Date the ECB fixed `rates` (yyyy-MM-dd), or null when there are none. */
  asOf: string | null;
  /** Visitor's country as the edge saw it, or null. Informational. */
  country: string | null;
}

const OFF: CurrencyState = { currency: CHARGE_CURRENCY, enabled: [CHARGE_CURRENCY], rates: {}, asOf: null, country: null };

let state: CurrencyState = OFF;
/** The edge's country-based suggestion from the last answer (EUR when unknown). */
let suggested: Currency = CHARGE_CURRENCY;
const listeners = new Set<() => void>();
const emit = (): void => listeners.forEach(l => l());

const sameState = (a: CurrencyState, b: CurrencyState): boolean =>
  a.currency === b.currency &&
  a.asOf === b.asOf &&
  a.country === b.country &&
  a.enabled.join(',') === b.enabled.join(',') &&
  a.enabled.every(c => a.rates[c] === b.rates[c]);

const setState = (next: CurrencyState): void => {
  if (sameState(state, next)) return;
  state = next;
  emit();
};

export const subscribeCurrency = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};
export const currencySnapshot = (): CurrencyState => state;
/** React hook: re-renders when the display currency, the enabled list or the rates change. */
export const useCurrency = (): CurrencyState => useSyncExternalStore(subscribeCurrency, currencySnapshot);

/** Display currency — for plain modules (money.ts). */
export const currentCurrency = (): Currency => state.currency;
/** Units of the display currency per one euro; 1 for EUR. */
export const currentRate = (): number => (state.currency === CHARGE_CURRENCY ? 1 : state.rates[state.currency] ?? 1);
/** True when prices on screen are converted, i.e. NOT the euro amounts that are charged. */
export const isConverted = (): boolean => state.currency !== CHARGE_CURRENCY;
/**
 * Changes whenever anything that affects a written price changes. App.tsx keys the
 * route tree on it, because most price renders call `money()` without subscribing.
 */
export const currencyRenderKey = (s: CurrencyState = state): string => `${s.currency}|${s.currency === CHARGE_CURRENCY ? '' : s.rates[s.currency]}`;

// ---------------------------------------------------------------------------
// Preference (query / cookie)
// ---------------------------------------------------------------------------

const readCookie = (): Currency | null => {
  if (typeof document === 'undefined') return null;
  const hit = document.cookie
    .split(';')
    .map(c => c.trim())
    .find(c => c.startsWith(`${CURRENCY_COOKIE}=`));
  const value = hit ? decodeURIComponent(hit.slice(CURRENCY_COOKIE.length + 1)).toUpperCase() : null;
  return isCurrency(value) ? value : null;
};

const writeCookie = (currency: Currency): void => {
  if (typeof document === 'undefined') return;
  const secure = typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${CURRENCY_COOKIE}=${currency}; Path=/; Max-Age=${COOKIE_MAX_AGE_SECONDS}; SameSite=Lax${secure}`;
};

const readQuery = (): Currency | null => {
  if (typeof location === 'undefined') return null;
  const value = (new URLSearchParams(location.search).get(CURRENCY_QUERY_PARAM) ?? '').toUpperCase();
  return isCurrency(value) ? value : null;
};

/** Query beats cookie beats the country suggestion; anything not enabled falls through to EUR. */
const resolve = (enabled: readonly Currency[], suggestion: Currency): Currency => {
  for (const candidate of [readQuery(), readCookie(), suggestion]) {
    if (candidate && enabled.includes(candidate)) return candidate;
  }
  return CHARGE_CURRENCY;
};

// ---------------------------------------------------------------------------
// Applying an edge answer
// ---------------------------------------------------------------------------

interface EdgeAnswer {
  country?: unknown;
  suggested?: unknown;
  enabled?: unknown;
  rates?: unknown;
  asOf?: unknown;
}

/** Normalises whatever the edge (or the cache) said; anything unusable degrades to EUR-only. */
const apply = (raw: EdgeAnswer | null | undefined): void => {
  const rates: Partial<Record<Currency, number>> = {};
  const enabled: Currency[] = [CHARGE_CURRENCY];
  const rawRates = raw && typeof raw.rates === 'object' && raw.rates ? (raw.rates as Record<string, unknown>) : {};
  for (const code of Array.isArray(raw?.enabled) ? (raw?.enabled as unknown[]) : []) {
    const c = String(code ?? '').toUpperCase();
    const rate = Number(rawRates[c]);
    // Enabled REQUIRES a usable rate: without one we could only show a wrong number.
    if (isCurrency(c) && c !== CHARGE_CURRENCY && !enabled.includes(c) && Number.isFinite(rate) && rate > 0) {
      enabled.push(c);
      rates[c] = rate;
    }
  }
  const ordered = SUPPORTED_CURRENCIES.filter(c => enabled.includes(c));
  const s = String(raw?.suggested ?? '').toUpperCase();
  suggested = isCurrency(s) && ordered.includes(s) ? s : CHARGE_CURRENCY;
  setState({
    currency: resolve(ordered, suggested),
    enabled: ordered,
    rates,
    asOf: ordered.length > 1 && typeof raw?.asOf === 'string' ? raw.asOf : null,
    country: typeof raw?.country === 'string' ? raw.country : null,
  });
};

const readCache = (): EdgeAnswer | null => {
  try {
    const parsed = JSON.parse(window.localStorage.getItem(CACHE_KEY) ?? 'null');
    if (!parsed || typeof parsed.savedAt !== 'number' || Date.now() - parsed.savedAt > CACHE_MAX_AGE_MS) return null;
    return parsed.answer ?? null;
  } catch {
    return null;
  }
};

const writeCache = (answer: EdgeAnswer | null): void => {
  try {
    if (answer) window.localStorage.setItem(CACHE_KEY, JSON.stringify({ savedAt: Date.now(), answer }));
    else window.localStorage.removeItem(CACHE_KEY);
  } catch {
    // private window / blocked storage: the live answer still applies
  }
};

// ---------------------------------------------------------------------------
// Public actions
// ---------------------------------------------------------------------------

/** The visitor picks a currency. Remembered in the cookie; ignored unless enabled. */
export const setCurrency = (currency: Currency): Currency => {
  const target = state.enabled.includes(currency) ? currency : CHARGE_CURRENCY;
  writeCookie(target);
  setState({ ...state, currency: target });
  return target;
};

let inFlight: Promise<void> | null = null;

/**
 * Boot: paint from yesterday's answer if there is one, then ask the edge. Called once
 * from index.tsx; never blocks the first render and never rejects.
 */
export const initCurrency = (): Promise<void> => {
  if (inFlight) return inFlight;
  const cached = readCache();
  if (cached) apply(cached);
  inFlight = fetch('/auth/currency', { headers: { Accept: 'application/json' } })
    .then(res => (res.ok ? res.json() : null))
    .then(env => {
      const data = env && env.errno === 0 && env.data ? (env.data as EdgeAnswer) : null;
      apply(data);
      writeCache(state.enabled.length > 1 ? data : null);
    })
    .catch(() => {
      // Outage / old edge: keep what the cache gave us for THIS page load only if it
      // exists; otherwise we are already on EUR. Never an error state.
    });
  return inFlight;
};

/** Test seam: apply an edge answer directly. */
export const __applyCurrencyAnswer = (answer: EdgeAnswer | null): void => apply(answer);

/** Test seam: back to boot state (EUR only, cookie + cache cleared). */
export const __resetCurrency = (): void => {
  if (typeof document !== 'undefined') document.cookie = `${CURRENCY_COOKIE}=; Path=/; Max-Age=0`;
  writeCache(null);
  inFlight = null;
  suggested = CHARGE_CURRENCY;
  state = OFF;
  emit();
};
