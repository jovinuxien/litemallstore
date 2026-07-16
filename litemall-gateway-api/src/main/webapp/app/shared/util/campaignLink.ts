/**
 * Shared UTM link-builder (Wave-6 convention, locked in the plan PDF):
 *
 *   utm_source   = facebook | instagram | tiktok
 *   utm_medium   = social
 *   utm_campaign = <slug>
 *
 * The SAME convention is implemented server-side by the promotion service's
 * social composer (its share URLs land here); this SPA-side twin exists for
 * client-generated share links so both produce byte-identical query params.
 * UTM params compose with the affiliate `?invite=` param: both are plain
 * query-string additions, React Router matches on pathname only, and Matomo
 * reads utm_* natively from the tracked URL — so a link can carry both.
 */

export type SocialUtmSource = 'facebook' | 'instagram' | 'tiktok';

export const UTM_MEDIUM_SOCIAL = 'social';

export interface CampaignParams {
  source: SocialUtmSource | string;
  campaign: string;
  /** Defaults to the locked social medium. */
  medium?: string;
}

/**
 * Add the UTM triplet to `link` (absolute URL or app-relative path starting
 * with `/`), preserving every existing query param (`?invite=` included) and
 * the hash. Relative input yields relative output.
 */
export const buildCampaignLink = (link: string, params: CampaignParams): string => {
  const relative = link.startsWith('/');
  const url = new URL(link, relative ? window.location.origin : undefined);
  url.searchParams.set('utm_source', params.source);
  url.searchParams.set('utm_medium', params.medium ?? UTM_MEDIUM_SOCIAL);
  url.searchParams.set('utm_campaign', params.campaign);
  return relative ? url.pathname + url.search + url.hash : url.toString();
};
