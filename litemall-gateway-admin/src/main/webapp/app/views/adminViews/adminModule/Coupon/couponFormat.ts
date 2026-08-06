import { ICoupon } from 'app/shared/model/admin/promotion-system.model';

// Wave 18: pure render/validation helpers for the coupon scope + percent
// surfaces (CouponForm / CouponList). Kept view-free so they are unit-testable.

// discountType codes (V51 discount_type)
export const DISCOUNT_FLAT = 0;
export const DISCOUNT_PERCENT = 1;

// goodsType codes (LitemallCouponGoodsType)
export const SCOPE_ALL = 0;
export const SCOPE_CATEGORY = 1;
export const SCOPE_GOODS = 2;

// Contract bounds: percent rate 1–90; product-scope picker cap 500.
export const PERCENT_MIN = 1;
export const PERCENT_MAX = 90;
export const MAX_SCOPE_GOODS = 500;

const money = (v: number): string => `$${Number(v).toFixed(2)}`;

// "10% off (up to $5.00)" | "$5.00 off" — for lists and read-only surfaces.
export const fmtCouponDiscount = (c: ICoupon): string => {
  if (c.discount == null) return '—';
  if ((c.discountType ?? DISCOUNT_FLAT) === DISCOUNT_PERCENT) {
    const rate = Number(c.discount);
    const shown = Number.isInteger(rate) ? String(rate) : rate.toFixed(1);
    return c.discountCap != null ? `${shown}% off (up to ${money(c.discountCap)})` : `${shown}% off`;
  }
  return `${money(Number(c.discount))} off`;
};

export const discountTypeLabel = (c: ICoupon): string => ((c.discountType ?? DISCOUNT_FLAT) === DISCOUNT_PERCENT ? 'percent' : 'flat');

// "All" | "Category (2)" | "Products (14)" — count only when ids are known.
export const couponScopeLabel = (c: ICoupon): string => {
  const n = c.goodsValue?.length ?? 0;
  switch (c.goodsType ?? SCOPE_ALL) {
    case SCOPE_CATEGORY:
      return n > 0 ? `Category (${n})` : 'Category';
    case SCOPE_GOODS:
      return n > 0 ? `Products (${n})` : 'Products';
    default:
      return 'All';
  }
};

// Client-side pre-validation mirroring the promotion-side rules; the server
// remains authoritative (its margin-guard rejections are surfaced verbatim).
// Returns the first blocking error, or null when the form may be submitted.
export const couponClientError = (c: ICoupon): string | null => {
  if ((c.discountType ?? DISCOUNT_FLAT) === DISCOUNT_PERCENT) {
    const rate = Number(c.discount);
    if (!Number.isFinite(rate) || rate < PERCENT_MIN || rate > PERCENT_MAX) {
      return `Percent rate must be between ${PERCENT_MIN} and ${PERCENT_MAX}.`;
    }
    if (c.discountCap != null && !(Number(c.discountCap) > 0)) {
      return 'Max discount cap must be a positive amount (or left empty).';
    }
  }
  const scope = c.goodsType ?? SCOPE_ALL;
  const n = c.goodsValue?.length ?? 0;
  if (scope === SCOPE_CATEGORY && n === 0) return 'Pick at least one category for a category-scoped coupon.';
  if (scope === SCOPE_GOODS && n === 0) return 'Pick at least one product for a product-scoped coupon.';
  if (scope === SCOPE_GOODS && n > MAX_SCOPE_GOODS) return `A product-scoped coupon can list at most ${MAX_SCOPE_GOODS} products.`;
  return null;
};
