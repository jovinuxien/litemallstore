import { ICoupon } from 'app/shared/api';
import { money } from 'app/shared/util/money';

/**
 * Wave-24.1 checkout money honesty — pure helpers.
 *
 * Coupon side: `/srv/coupon/selectlist?verbose=true` returns
 * `{usable, unusable:[{...coupon, reason, minGap?}]}` with typed reasons
 * `threshold|scope|expired|exhausted`; WITHOUT the param (or against the
 * pre-24.1 promotion service) the response is the legacy BARE ARRAY.
 * `parseSelectlist` normalizes both so the checkout renders whatever the
 * backend can say — old backend ⇒ usable-only, exactly today.
 *
 * Courier side: the chooser labels each CJ line with its upgrade delta
 * relative to the server's default line ("Included" / "+€x.xx"). Prices
 * arrive post-fx on the quote's options[] (24.1 order half); until that
 * field ships the label is null and the chooser renders priceless as today.
 */

export interface UnusableCoupon extends ICoupon {
  reason?: string;
  minGap?: number;
}

export interface SelectlistBuckets {
  usable: ICoupon[];
  unusable: UnusableCoupon[];
}

export const parseSelectlist = (res: unknown): SelectlistBuckets => {
  if (Array.isArray(res)) return { usable: res as ICoupon[], unusable: [] };
  if (res != null && typeof res === 'object') {
    const r = res as { usable?: unknown; unusable?: unknown };
    return {
      usable: Array.isArray(r.usable) ? (r.usable as ICoupon[]) : [],
      unusable: Array.isArray(r.unusable) ? (r.unusable as UnusableCoupon[]) : [],
    };
  }
  return { usable: [], unusable: [] };
};

/** Honest copy for a typed unusable-reason; server vocabulary, our words. */
export const unusableReasonLabel = (reason?: string, minGap?: number): string => {
  switch (reason) {
    case 'threshold':
      return Number.isFinite(Number(minGap)) && Number(minGap) > 0
        ? `Spend ${money(Number(minGap))} more to use this coupon`
        : 'Order total is below this coupon’s minimum';
    case 'scope':
      return 'Not valid for these items';
    case 'expired':
      return 'Expired';
    case 'exhausted':
      return 'Fully claimed';
    default:
      return 'Not usable for this order';
  }
};

/**
 * Label for the server's `options[].upgradeDelta` — rendered VERBATIM, no
 * client-side price math (raw CJ courier costs are never surfaced): 0.00 (or
 * anything ≤0) ⇒ "Included", positive ⇒ "+€x.xx", null/absent ⇒ null and the
 * caller renders no label (unpriceable line, or the pre-24.1 order half).
 */
export const courierDeltaLabel = (upgradeDelta?: number | null): string | null => {
  if (upgradeDelta == null) return null;
  const delta = Number(upgradeDelta);
  if (!Number.isFinite(delta)) return null;
  return delta > 0.004 ? `+${money(delta)}` : 'Included';
};
