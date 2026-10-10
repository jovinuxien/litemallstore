import { TFunction } from 'i18next';

import { IPageView } from 'app/shared/api';
import { seasonKey } from 'app/shared/util/season';

/**
 * The season chip on a product card.
 *
 * goods-management scores EVERY season nightly and ships the memberships on
 * each search hit and list item as `seasons: ["autumn","winter"]` — a wool
 * throw legitimately belongs to two. Rendering all of them would put "Summer"
 * on cards in October, so the chip names ONE season: the one the store is
 * running, read off the active season page's rail (`seasonKey`). No season
 * running, a hand-curated rail with no key, or a product outside it ⇒ no chip.
 *
 * Like the EU-stock chip next to it, this is NOT an offer: it never competes
 * for the single overlay badge and never displaces a deal.
 */

/** The hit's season memberships, normalised: lowercase keys, junk dropped, a bare string tolerated. */
export const productSeasons = (raw: unknown): string[] => {
  const list = Array.isArray(raw) ? raw : typeof raw === 'string' ? [raw] : [];
  return list.filter((k): k is string => typeof k === 'string').map(k => k.trim().toLowerCase()).filter(k => k.length > 0);
};

/** The chip to show for this product under the running season, or null. */
export const seasonChip = (product: unknown, season: IPageView | null): string | null => {
  const key = seasonKey(season);
  if (!key) return null;
  const seasons = productSeasons((product as { seasons?: unknown } | null)?.seasons);
  return seasons.includes(key) ? key : null;
};

/**
 * The chip's wording. The four seasons goods-management ships today have
 * catalogue entries; a key added later (spec: "and anything else later")
 * renders capitalised rather than as a raw token or a missing-key string,
 * and the parity spec will flag the missing catalogue entry.
 */
export const seasonName = (key: string, t: TFunction): string => {
  switch (key) {
    case 'autumn':
      return t('product:card.seasons.autumn');
    case 'winter':
      return t('product:card.seasons.winter');
    case 'spring':
      return t('product:card.seasons.spring');
    case 'summer':
      return t('product:card.seasons.summer');
    default:
      return key.charAt(0).toUpperCase() + key.slice(1);
  }
};
