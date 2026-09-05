import { useSyncExternalStore } from 'react';

import { loadSiteConfig } from 'app/shared/config/siteConfig';

import { DEFAULT_LANGUAGE, i18n, isLang, type Lang, SUPPORTED_LANGUAGES } from './index';

/**
 * The storefront's locale store — the single answer to "which language is the shop in
 * right now", readable from React (`useLocale`) and from plain modules (`currentLocale`,
 * which money.ts uses to pick a number format).
 *
 * Same tiny observable shape as `shared/config/siteConfig.ts` and
 * `shared/tracking/consent.ts`, and deliberately not Redux for the same reason: the money
 * formatter runs outside React.
 *
 * DETECTION ORDER (first hit wins): `?lang=` query → cookie `lm_lang` → the browser's
 * languages (only sv/da count; anything else is English) → English. Unknown values fall
 * back to English, never to a raw key.
 *
 * ENABLED LANGUAGES come from `/auth/site-config` (`i18nLanguages`, env
 * `LITEMALL_I18N_LANGUAGES`, default `en`). This is the same absent-means-hidden gate as
 * every other storefront feature: until an operator lists `sv`/`da`, the switcher does
 * not render and a Swedish browser gets English. An explicit earlier choice (cookie or
 * `?lang=`) is applied immediately at boot — the visitor could only have made it while
 * the language was enabled — and is pulled back to English if the enabled list has
 * since shrunk. A browser-language preference waits for the enabled list, so a
 * not-yet-reviewed translation never flashes on first paint.
 *
 * The language is NOT part of the URL in this phase (see docs/spec-i18n-foundation.md
 * §3.3): catalogue content stays English, so per-language URLs would only create
 * duplicate pages. `<html lang>` follows the choice so assistive tech and spell-checkers
 * see the right language.
 */

export const LANG_COOKIE = 'lm_lang';
export const LANG_QUERY_PARAM = 'lang';
const COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

export interface LocaleState {
  /** Active language (what i18next is rendering). */
  lang: Lang;
  /** Languages the operator has enabled; always contains 'en'. */
  enabled: Lang[];
}

/** BCP-47 tag used for number/date formatting. Region picks the € placement + separators. */
const FORMAT_TAGS: Record<Lang, string> = { en: 'en-IE', sv: 'sv-SE', da: 'da-DK' };

let state: LocaleState = { lang: DEFAULT_LANGUAGE, enabled: [DEFAULT_LANGUAGE] };
const listeners = new Set<() => void>();
const emit = (): void => listeners.forEach(l => l());

const setState = (next: LocaleState): void => {
  if (next.lang === state.lang && next.enabled.join(',') === state.enabled.join(',')) return;
  state = next;
  emit();
};

export const subscribeLocale = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const localeSnapshot = (): LocaleState => state;

/** Active language — for plain modules. */
export const currentLocale = (): Lang => state.lang;

/** BCP-47 formatting tag for the active language ('en-IE' | 'sv-SE' | 'da-DK'). */
export const currentLocaleTag = (): string => FORMAT_TAGS[state.lang];

export const localeTagFor = (lang: Lang): string => FORMAT_TAGS[lang];

/** React hook: re-renders on language or enabled-list changes. Snapshot is referentially stable. */
export const useLocale = (): LocaleState => useSyncExternalStore(subscribeLocale, localeSnapshot);

// ---------------------------------------------------------------------------
// Detection
// ---------------------------------------------------------------------------

export const readLangCookie = (): Lang | null => {
  if (typeof document === 'undefined') return null;
  const hit = document.cookie
    .split(';')
    .map(c => c.trim())
    .find(c => c.startsWith(`${LANG_COOKIE}=`));
  const value = hit ? decodeURIComponent(hit.slice(LANG_COOKIE.length + 1)) : null;
  return isLang(value) ? value : null;
};

const writeLangCookie = (lang: Lang): void => {
  if (typeof document === 'undefined') return;
  const secure = typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : '';
  // Readable by the SPA on purpose (no HttpOnly): the SPA is the one that sets and reads
  // it. SameSite=Lax so a shared link still opens in the visitor's own language.
  document.cookie = `${LANG_COOKIE}=${encodeURIComponent(lang)}; Path=/; Max-Age=${COOKIE_MAX_AGE_SECONDS}; SameSite=Lax${secure}`;
};

const readQueryLang = (): Lang | null => {
  if (typeof location === 'undefined') return null;
  const value = new URLSearchParams(location.search).get(LANG_QUERY_PARAM);
  return isLang(value) ? value : null;
};

/** First sv/da the browser prefers, else null (English is the default anyway). */
export const browserPreferredLang = (languages: readonly string[] = typeof navigator !== 'undefined' ? navigator.languages ?? [navigator.language] : []): Lang | null => {
  for (const raw of languages) {
    const base = String(raw ?? '').toLowerCase().split('-')[0];
    if (isLang(base) && base !== DEFAULT_LANGUAGE) return base;
    if (base === DEFAULT_LANGUAGE) return null; // English ranked above any sv/da: stop.
  }
  return null;
};

/**
 * What the visitor has explicitly asked for (query beats cookie), or null when the only
 * signal is the browser's own language list.
 */
export const explicitPreference = (): Lang | null => readQueryLang() ?? readLangCookie();

// ---------------------------------------------------------------------------
// Switching
// ---------------------------------------------------------------------------

const applyToDocument = (lang: Lang): void => {
  if (typeof document !== 'undefined' && document.documentElement) {
    document.documentElement.lang = lang;
  }
};

/**
 * Switch the storefront language. Resolves once the language's resources are loaded (a
 * first switch to sv/da fetches its chunks). `persist:false` applies without writing the
 * cookie — used for the boot-time browser-language default so a mere visit never pins a
 * choice the visitor did not make.
 */
export const setLocale = async (lang: Lang, opts: { persist?: boolean } = {}): Promise<Lang> => {
  const persist = opts.persist ?? true;
  const target = state.enabled.includes(lang) ? lang : DEFAULT_LANGUAGE;
  if (persist) writeLangCookie(target);
  if (i18n.language !== target || !i18n.hasLoadedNamespace('common', { lng: target })) {
    try {
      await i18n.changeLanguage(target);
    } catch {
      // A failed chunk fetch leaves i18next on the previous language; English strings
      // remain as fallback either way. Never a broken page.
    }
  }
  const active = isLang(i18n.resolvedLanguage) ? i18n.resolvedLanguage : DEFAULT_LANGUAGE;
  applyToDocument(active);
  setState({ lang: active, enabled: state.enabled });
  return active;
};

/**
 * Register the operator's enabled list. English is always enabled; unknown codes are
 * dropped. If the active language is no longer enabled, fall back to English; if the
 * browser prefers an enabled sv/da and the visitor never chose explicitly, switch to it.
 */
export const applyEnabledLanguages = async (codes: readonly unknown[] | null | undefined): Promise<void> => {
  const enabled: Lang[] = [DEFAULT_LANGUAGE];
  for (const raw of codes ?? []) {
    const code = String(raw ?? '').trim().toLowerCase();
    if (isLang(code) && !enabled.includes(code)) enabled.push(code);
  }
  // Keep the canonical order so the switcher is stable regardless of env ordering.
  const ordered = SUPPORTED_LANGUAGES.filter(l => enabled.includes(l));
  setState({ lang: state.lang, enabled: ordered });

  if (!ordered.includes(state.lang)) {
    await setLocale(DEFAULT_LANGUAGE, { persist: false });
    return;
  }
  if (explicitPreference() == null) {
    const preferred = browserPreferredLang();
    if (preferred && ordered.includes(preferred) && preferred !== state.lang) {
      await setLocale(preferred, { persist: false });
    }
  }
};

/**
 * Boot: apply an explicit preference right away (cookie / `?lang=`), then reconcile with
 * the enabled list once site-config answers. Called once from index.tsx. Safe to call in
 * jest: with no cookie, no query and an unloaded site-config it leaves English in place.
 */
export const initLocale = (): Promise<void> => {
  applyToDocument(state.lang);
  const explicit = explicitPreference();
  // Enabled list is unknown yet; trust the explicit choice provisionally (it is pulled
  // back by applyEnabledLanguages if the operator has since disabled it).
  if (explicit && explicit !== state.lang) {
    setState({ lang: state.lang, enabled: SUPPORTED_LANGUAGES.slice() });
  }
  const first = explicit ? setLocale(explicit, { persist: readQueryLang() != null }) : Promise.resolve(state.lang);
  return first
    .then(() => loadSiteConfig())
    .then(cfg => applyEnabledLanguages(cfg.i18nLanguages))
    .catch(() => applyEnabledLanguages([]));
};

/** Test seam: back to boot state (English, en-only, cookie cleared). */
export const __resetLocale = async (): Promise<void> => {
  if (typeof document !== 'undefined') {
    document.cookie = `${LANG_COOKIE}=; Path=/; Max-Age=0`;
  }
  state = { lang: DEFAULT_LANGUAGE, enabled: [DEFAULT_LANGUAGE] };
  if (i18n.language !== DEFAULT_LANGUAGE) await i18n.changeLanguage(DEFAULT_LANGUAGE);
  applyToDocument(DEFAULT_LANGUAGE);
  emit();
};
