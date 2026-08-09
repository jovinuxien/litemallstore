import { describe, expect, it } from '@jest/globals';

import {
  DISCOUNT_PERCENT,
  MAX_SCOPE_GOODS,
  SCOPE_ALL,
  SCOPE_CATEGORY,
  SCOPE_GOODS,
  couponClientError,
  couponScopeLabel,
  discountTypeLabel,
  fmtCouponDiscount,
} from './couponFormat';

// Wave 18: coupon scope + percent rendering/validation helpers.

describe('fmtCouponDiscount', () => {
  it('renders a flat coupon as "€D off"', () => {
    expect(fmtCouponDiscount({ discount: 5 })).toBe('€5.00 off');
    expect(fmtCouponDiscount({ discount: 5, discountType: 0 })).toBe('€5.00 off');
  });

  it('renders a percent coupon with and without cap', () => {
    expect(fmtCouponDiscount({ discount: 10, discountType: DISCOUNT_PERCENT })).toBe('10% off');
    expect(fmtCouponDiscount({ discount: 12.5, discountType: DISCOUNT_PERCENT, discountCap: 5 })).toBe('12.5% off (up to €5.00)');
  });

  it('renders a dash when the discount is unknown', () => {
    expect(fmtCouponDiscount({})).toBe('—');
  });
});

describe('discountTypeLabel', () => {
  it('defaults pre-V51 rows (no discountType) to flat', () => {
    expect(discountTypeLabel({})).toBe('flat');
    expect(discountTypeLabel({ discountType: DISCOUNT_PERCENT })).toBe('percent');
  });
});

describe('couponScopeLabel', () => {
  it('maps goodsType to All / Category / Products with id counts', () => {
    expect(couponScopeLabel({})).toBe('All');
    expect(couponScopeLabel({ goodsType: SCOPE_ALL })).toBe('All');
    expect(couponScopeLabel({ goodsType: SCOPE_CATEGORY, goodsValue: [1005000] })).toBe('Category (1)');
    expect(couponScopeLabel({ goodsType: SCOPE_GOODS, goodsValue: [1, 2, 3] })).toBe('Products (3)');
    expect(couponScopeLabel({ goodsType: SCOPE_GOODS })).toBe('Products');
  });
});

describe('couponClientError', () => {
  it('accepts a valid flat all-goods coupon', () => {
    expect(couponClientError({ discount: 5 })).toBeNull();
  });

  it('bounds the percent rate to 1–90', () => {
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 0.5 })).toMatch(/between 1 and 90/);
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 95 })).toMatch(/between 1 and 90/);
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: NaN })).toMatch(/between 1 and 90/);
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 1 })).toBeNull();
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 90 })).toBeNull();
  });

  it('rejects a non-positive percent cap but allows an absent one', () => {
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 10, discountCap: 0 })).toMatch(/cap/i);
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 10, discountCap: 5 })).toBeNull();
    expect(couponClientError({ discountType: DISCOUNT_PERCENT, discount: 10 })).toBeNull();
  });

  it('requires at least one id for category / product scopes', () => {
    expect(couponClientError({ discount: 5, goodsType: SCOPE_CATEGORY })).toMatch(/category/i);
    expect(couponClientError({ discount: 5, goodsType: SCOPE_CATEGORY, goodsValue: [] })).toMatch(/category/i);
    expect(couponClientError({ discount: 5, goodsType: SCOPE_CATEGORY, goodsValue: [1005000] })).toBeNull();
    expect(couponClientError({ discount: 5, goodsType: SCOPE_GOODS })).toMatch(/product/i);
    expect(couponClientError({ discount: 5, goodsType: SCOPE_GOODS, goodsValue: [10008302] })).toBeNull();
  });

  it('caps a product-scoped coupon at 500 ids', () => {
    const ids = Array.from({ length: MAX_SCOPE_GOODS + 1 }, (_, i) => i + 1);
    expect(couponClientError({ discount: 5, goodsType: SCOPE_GOODS, goodsValue: ids })).toMatch(/500/);
    expect(couponClientError({ discount: 5, goodsType: SCOPE_GOODS, goodsValue: ids.slice(0, MAX_SCOPE_GOODS) })).toBeNull();
  });
});
