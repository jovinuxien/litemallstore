/**
 * Runtime site config from `GET /auth/site-config` (Wave-6 seam, Wave-7 consumers).
 *
 * <p>Wave-6 fetched this in `MatomoTracker` and kept the result in module-local
 * tracker state, which was fine while tracking was the only consumer. Wave-7 adds a
 * second (Stripe Elements needs the publishable key), so the fetch moves here: one
 * request, shared, rather than each feature fetching its own.
 *
 * <p>Deliberately not Redux. The tracker reads this from plain module code outside
 * React (see matomo.ts), so the store has to be usable without a hook; the same tiny
 * observable shape as `shared/tracking/consent.ts`.
 *
 * <p>Fetch failure is not an error state — it resolves to "nothing configured", which
 * every consumer already treats as "feature off". A site-config outage therefore
 * degrades to no tracking and no card payment, never to a broken page or a stub.
 */

export interface SiteConfig {
  matomoUrl: string | null;
  matomoSiteId: string | null;
  matomoGoodsDimension?: number;
  /** Stripe publishable key. Null ⇒ card payment unavailable — never stub it. */
  stripePublishableKey: string | null;
  /** Meta Pixel id (Wave 15). Null ⇒ no pixel is injected, ever. */
  metaPixelId: string | null;
  /** Google OAuth client id (Wave 16). Null ⇒ no Google button anywhere. */
  googleClientId: string | null;
  /** Places autocomplete key (Wave 16). Null ⇒ plain manual address fields. */
  placesApiKey: string | null;
  /** Social profile URLs (Wave-9.1). Null ⇒ that network's icon is not rendered. */
  socialFacebookUrl: string | null;
  socialInstagramUrl: string | null;
  socialTiktokUrl: string | null;
  socialYoutubeUrl: string | null;
  socialXUrl: string | null;
}

const EMPTY: SiteConfig = {
  matomoUrl: null,
  matomoSiteId: null,
  stripePublishableKey: null,
  metaPixelId: null,
  googleClientId: null,
  placesApiKey: null,
  socialFacebookUrl: null,
  socialInstagramUrl: null,
  socialTiktokUrl: null,
  socialYoutubeUrl: null,
  socialXUrl: null,
};

export interface SiteConfigState {
  loaded: boolean;
  config: SiteConfig;
}

let state: SiteConfigState = { loaded: false, config: EMPTY };
let inFlight: Promise<SiteConfig> | null = null;

const listeners = new Set<() => void>();
const emit = (): void => listeners.forEach(l => l());

export const subscribeSiteConfig = (listener: () => void): (() => void) => {
  listeners.add(listener);
  return () => listeners.delete(listener);
};

export const siteConfigSnapshot = (): SiteConfigState => state;

/**
 * Fetch once per page load; concurrent callers share the same request. Resolves to the
 * config so non-React callers can await it.
 */
export const loadSiteConfig = (): Promise<SiteConfig> => {
  if (state.loaded) return Promise.resolve(state.config);
  if (inFlight) return inFlight;
  inFlight = fetch('/auth/site-config')
    .then(res => res.json())
    .then(env => ({ ...EMPTY, ...(env?.data ?? {}) }) as SiteConfig)
    .catch(() => EMPTY)
    .then(config => {
      state = { loaded: true, config };
      emit();
      inFlight = null;
      return config;
    });
  return inFlight;
};
