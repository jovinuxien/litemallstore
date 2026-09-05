import { ICoupon, userApi } from 'app/shared/api';
import { t } from 'app/i18n';
import { EURO } from 'app/shared/util/money';

/**
 * Wave-18 coupon rendering helpers — ONE place that knows the flat-vs-percent
 * contract so every surface (PDP strip, coupon center, my coupons, checkout
 * picker) says the same thing.
 *
 * Contract facts (Wave-18, CLAUDE.md):
 *  - `discountType` 0/absent = flat euros off; 1 = percent-off where
 *    `discount` holds the RATE (1–90) and `discountCap` an optional euro cap.
 *  - EXCEPTION: `/srv/coupon/selectlist` returns the COMPUTED effective euro
 *    discount in `discount` for BOTH types (order math is unchanged) — use
 *    {@link couponPickerLabel} there, never the rate-based labels.
 *  - Pre-Wave-18 payloads carry none of the new fields: absent ⇒ flat,
 *    unscoped. Every helper must degrade to today's behavior.
 */

export const isPercentCoupon = (c: ICoupon): boolean => c.discountType === 1;

const cap = (c: ICoupon): string => (isPercentCoupon(c) && c.discountCap ? t('coupon:format.capParen', { amount: `${EURO}${c.discountCap}` }) : '');

/** Compact value for card/strip headings: "15%" or "€5". */
export const couponValueShort = (c: ICoupon): string =>
  isPercentCoupon(c) ? `${c.discount ?? 0}%` : `${EURO}${c.discount ?? 0}`;

/** Full sentence label: "15% off (up to €20)" or "€5 off". */
export const couponDiscountLabel = (c: ICoupon): string =>
  isPercentCoupon(c) ? `${t('coupon:format.percentOff', { rate: c.discount ?? 0 })}${cap(c)}` : t('coupon:format.flatOff', { amount: `${EURO}${c.discount ?? 0}` });

/** Threshold line: "Spend €50" / "No minimum", with the percent cap appended. */
export const couponConditionLabel = (c: ICoupon): string =>
  `${c.min ? t('coupon:format.spend', { amount: `${EURO}${c.min}` }) : t('coupon:format.noMinimum')}${isPercentCoupon(c) && c.discountCap ? ` · ${t('coupon:format.upTo', { amount: `${EURO}${c.discountCap}` })}` : ''}`;

/**
 * Checkout-picker option text. `c` comes from selectlist, so `discount` is the
 * server-computed effective EURO discount for this cart even for percent
 * coupons — render euros (that is what will be charged) and flag the type.
 */
export const couponPickerLabel = (c: ICoupon): string => {
  const name = c.name ? `${c.name} — ` : '';
  const min = c.min ? t('coupon:format.pickerOver', { amount: `${EURO}${c.min}` }) : '';
  const kind = isPercentCoupon(c)
    ? c.discountCap
      ? t('coupon:format.pickerPercentCap', { amount: `${EURO}${c.discountCap}` })
      : t('coupon:format.pickerPercent')
    : '';
  return `${name}−${EURO}${c.discount}${min}${kind}`;
};

/** Scope target for "shop eligible items": category landing, PDP, or search. */
export const couponScopeLink = (c: ICoupon): { to: string; label: string } => {
  const ids = Array.isArray(c.goodsValue) ? c.goodsValue.filter(id => Number.isFinite(id)) : [];
  if (c.goodsType === 1 && ids.length > 0) {
    return { to: `/category/${ids[0]}`, label: t('coupon:format.shopEligible') };
  }
  if (c.goodsType === 2 && ids.length === 1) {
    return { to: `/product/${ids[0]}`, label: t('coupon:format.viewEligible') };
  }
  if (c.goodsType === 2 && ids.length > 1) {
    // No multi-goods landing exists; search is the honest fallback.
    return { to: '/search', label: t('coupon:format.shopStore') };
  }
  return { to: '/search', label: t('coupon:format.shopAll') };
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
