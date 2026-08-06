import { ICoupon, userApi } from 'app/shared/api';

/**
 * Wave-18 coupon rendering helpers — ONE place that knows the flat-vs-percent
 * contract so every surface (PDP strip, coupon center, my coupons, checkout
 * picker) says the same thing.
 *
 * Contract facts (Wave-18, CLAUDE.md):
 *  - `discountType` 0/absent = flat dollars off; 1 = percent-off where
 *    `discount` holds the RATE (1–90) and `discountCap` an optional dollar cap.
 *  - EXCEPTION: `/srv/coupon/selectlist` returns the COMPUTED effective dollar
 *    discount in `discount` for BOTH types (order math is unchanged) — use
 *    {@link couponPickerLabel} there, never the rate-based labels.
 *  - Pre-Wave-18 payloads carry none of the new fields: absent ⇒ flat,
 *    unscoped. Every helper must degrade to today's behavior.
 */

export const isPercentCoupon = (c: ICoupon): boolean => c.discountType === 1;

const cap = (c: ICoupon): string => (isPercentCoupon(c) && c.discountCap ? ` (up to $${c.discountCap})` : '');

/** Compact value for card/strip headings: "15%" or "$5". */
export const couponValueShort = (c: ICoupon): string =>
  isPercentCoupon(c) ? `${c.discount ?? 0}%` : `$${c.discount ?? 0}`;

/** Full sentence label: "15% off (up to $20)" or "$5 off". */
export const couponDiscountLabel = (c: ICoupon): string =>
  isPercentCoupon(c) ? `${c.discount ?? 0}% off${cap(c)}` : `$${c.discount ?? 0} off`;

/** Threshold line: "Spend $50" / "No minimum", with the percent cap appended. */
export const couponConditionLabel = (c: ICoupon): string =>
  `${c.min ? `Spend $${c.min}` : 'No minimum'}${isPercentCoupon(c) && c.discountCap ? ` · up to $${c.discountCap}` : ''}`;

/**
 * Checkout-picker option text. `c` comes from selectlist, so `discount` is the
 * server-computed effective DOLLAR discount for this cart even for percent
 * coupons — render dollars (that is what will be charged) and flag the type.
 */
export const couponPickerLabel = (c: ICoupon): string => {
  const name = c.name ? `${c.name} — ` : '';
  const min = c.min ? ` (over $${c.min})` : '';
  const kind = isPercentCoupon(c) ? ` · percent coupon${c.discountCap ? `, up to $${c.discountCap}` : ''}` : '';
  return `${name}−$${c.discount}${min}${kind}`;
};

/** Scope target for "shop eligible items": category landing, PDP, or search. */
export const couponScopeLink = (c: ICoupon): { to: string; label: string } => {
  const ids = Array.isArray(c.goodsValue) ? c.goodsValue.filter(id => Number.isFinite(id)) : [];
  if (c.goodsType === 1 && ids.length > 0) {
    return { to: `/category/${ids[0]}`, label: 'Shop eligible items' };
  }
  if (c.goodsType === 2 && ids.length === 1) {
    return { to: `/product/${ids[0]}`, label: 'View eligible product' };
  }
  if (c.goodsType === 2 && ids.length > 1) {
    // No multi-goods landing exists; search is the honest fallback.
    return { to: '/search', label: 'Shop the store' };
  }
  return { to: '/search', label: 'Shop all products' };
};

/**
 * Wave-18 register-gift trigger: grant the signup coupons to the freshly
 * authenticated user. STRICTLY fire-and-forget — the promise is swallowed so
 * a promotion-service hiccup can never block or fail signup, and the endpoint
 * is idempotent server-side so double-fires are harmless.
 */
export const fireRegisterGifts = (): void => {
  try {
    void userApi.couponRegisterGifts().catch(() => {
      /* fail-silent by contract */
    });
  } catch {
    /* fail-silent by contract */
  }
};
