import i18next, { type i18n as I18n, type TFunction } from 'i18next';
import { initReactI18next } from 'react-i18next';

import enAuth from './locales/en/auth.json';
import enCart from './locales/en/cart.json';
import enCheckout from './locales/en/checkout.json';
import enCoupon from './locales/en/coupon.json';
import enCommon from './locales/en/common.json';
import enErrors from './locales/en/errors.json';
import enOrder from './locales/en/order.json';
import enProduct from './locales/en/product.json';
import enSearch from './locales/en/search.json';
import enUser from './locales/en/user.json';

/**
 * Storefront i18n — the ONE module that initialises i18next. Import `useTranslation`,
 * `Trans` and `t` from here (not from react-i18next directly) so that any component or
 * helper rendering text is guaranteed an initialised instance, in the app and in jest
 * alike.
 *
 * Layout (mirrors the JHipster reference: one folder per language, one file per feature):
 *   i18n/locales/<lang>/<namespace>.json
 *
 * English is BUNDLED and registered synchronously (`initImmediate: false`), so the first
 * paint never waits on a fetch and a missing key can never show as a raw
 * `common:header.cart` — i18next falls back to the English string. Swedish and Danish
 * are loaded lazily through webpack `import()`: each language+namespace becomes its own
 * hashed chunk, fetched only after a visitor actually switches (see `lazyBackend`).
 *
 * Detection order and persistence live in `./locale.ts`; this module only knows how to
 * translate. It deliberately imports nothing from the rest of the app (it is imported by
 * money.ts, http.ts and the auth slice — a cycle here would be a boot-order bug).
 */

export const SUPPORTED_LANGUAGES = ['en', 'sv', 'da'] as const;
export type Lang = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: Lang = 'en';

/** Endonyms — a language's own name is never translated. */
export const LANGUAGE_NAMES: Record<Lang, string> = { en: 'English', sv: 'Svenska', da: 'Dansk' };

export const NAMESPACES = ['common', 'cart', 'checkout', 'auth', 'errors', 'product', 'search', 'coupon', 'order', 'user'] as const;
export type Namespace = (typeof NAMESPACES)[number];

export const isLang = (value: unknown): value is Lang =>
  typeof value === 'string' && (SUPPORTED_LANGUAGES as readonly string[]).includes(value);

/**
 * Lazy resource loader. i18next asks `read(lng, ns)` for any (language, namespace) pair
 * not already in `resources`; `partialBundledLanguages` keeps English out of that path.
 * The template import makes webpack emit one chunk per sv/da JSON (`webpackExclude` keeps
 * the bundled English files from being emitted a second time).
 */
const lazyBackend = {
  type: 'backend' as const,
  init(): void {
    /* nothing to configure */
  },
  read(lng: string, ns: string, callback: (err: unknown, data?: unknown) => void): void {
    import(
      /* webpackChunkName: "i18n-[request]" */
      /* webpackExclude: /\/en\// */
      `./locales/${lng}/${ns}.json`
    )
      .then(mod => callback(null, (mod as { default?: unknown }).default ?? mod))
      .catch(err => callback(err));
  },
};

if (!i18next.isInitialized) {
  void i18next
    .use(lazyBackend)
    .use(initReactI18next)
    .init({
      lng: DEFAULT_LANGUAGE,
      fallbackLng: DEFAULT_LANGUAGE,
      supportedLngs: [...SUPPORTED_LANGUAGES],
      // 'sv-SE' from a browser must resolve to our 'sv' file, never to a 404.
      load: 'languageOnly',
      nonExplicitSupportedLngs: true,
      ns: [...NAMESPACES],
      defaultNS: 'common',
      resources: {
        en: { common: enCommon, cart: enCart, checkout: enCheckout, auth: enAuth, errors: enErrors, product: enProduct, search: enSearch, coupon: enCoupon, order: enOrder, user: enUser },
      },
      partialBundledLanguages: true,
      // Synchronous init: English is in memory, so `t()` works on the very first render.
      initImmediate: false,
      returnNull: false,
      returnEmptyString: false,
      // React escapes for us; double-escaping would render `&amp;` in "Account & Lists".
      interpolation: { escapeValue: false },
      react: { useSuspense: false },
    });
}

export const i18n: I18n = i18next;

/** Non-React translation (helpers, slices, formatters). Same instance, same language. */
export const t: TFunction = i18next.t.bind(i18next) as TFunction;

export { Trans, useTranslation } from 'react-i18next';
export type { TFunction } from 'i18next';
