import { describe, expect, it } from '@jest/globals';

import { IPromoCandidate } from 'app/shared/reducers/private/services/insightApi';
import { DISCOUNT_PERCENT, SCOPE_CATEGORY, SCOPE_GOODS } from 'app/views/adminViews/adminModule/Coupon/couponFormat';
import { couponPrefill, createdRefId, fmtPromoSuggestion, grouponPrefill } from './promoFormat';

// Wave 19: promo-suggestion rendering + CouponForm/GrouponRuleForm prefill
// mapping (router-state contract) + created-id extraction for `consume`.

const couponCandidate = (suggestion: IPromoCandidate['suggestion']): IPromoCandidate => ({
  goodsId: 10008302,
  name: 'Linen Shirt',
  picUrl: '/_cdn/cf/shirt.jpg',
  categoryId: 1300,
  kind: 'coupon',
  day: '2026-08-08',
  cost: 10,
  retailPrice: 25,
  suggestion,
});

const grouponCandidate = (suggestion: IPromoCandidate['suggestion']): IPromoCandidate => ({
  ...couponCandidate(suggestion),
  kind: 'groupon',
});

describe('fmtPromoSuggestion', () => {
  it('renders a percent category coupon with cap and min spend', () => {
    const c = couponCandidate({
      scopeType: 'category',
      categoryId: 1300,
      discountType: DISCOUNT_PERCENT,
      discount: 10,
      discountCap: 5,
      minAmount: 30,
    });
    expect(fmtPromoSuggestion(c, "Women's Clothing")).toBe("10% off Women's Clothing, cap $5.00, min spend $30.00");
  });

  it('falls back to the category id when the name is unknown', () => {
    const c = couponCandidate({ scopeType: 'category', categoryId: 1300, discountType: DISCOUNT_PERCENT, discount: 12.5 });
    expect(fmtPromoSuggestion(c)).toBe('12.5% off category #1300');
  });

  it('renders a flat single-product coupon without cap/min noise', () => {
    const c = couponCandidate({ scopeType: 'goods', goodsIds: [10008302], discountType: 0, discount: 4, minAmount: 0 });
    expect(fmtPromoSuggestion(c)).toBe('$4.00 off this product');
  });

  it('counts a multi-product scope', () => {
    const c = couponCandidate({ scopeType: 'goods', goodsIds: [1, 2, 3], discountType: DISCOUNT_PERCENT, discount: 15 });
    expect(fmtPromoSuggestion(c)).toBe('15% off 3 products');
  });

  it('renders a groupon suggestion as price + members + window + limit', () => {
    const c = grouponCandidate({ combinationPrice: 12.99, originalPrice: 25, requiredMembers: 3, limitPerUser: 1, windowDays: 7 });
    expect(fmtPromoSuggestion(c)).toBe('group price $12.99, 3 members, 7-day window, limit 1/user');
  });

  it('renders a dash when the suggestion is missing (uncaptured cost)', () => {
    expect(fmtPromoSuggestion(couponCandidate(null))).toBe('—');
    expect(fmtPromoSuggestion(couponCandidate({ scopeType: 'category', categoryId: 1300 }))).toBe('—');
    expect(fmtPromoSuggestion(grouponCandidate({}))).toBe('—');
  });
});

describe('couponPrefill', () => {
  it('maps a category-scoped percent suggestion onto CouponForm fields', () => {
    const c = couponCandidate({
      scopeType: 'category',
      categoryId: 1300,
      discountType: DISCOUNT_PERCENT,
      discount: 10,
      discountCap: 5,
      minAmount: 30,
    });
    const { coupon, consume } = couponPrefill(c, "Women's Clothing");
    expect(coupon.goodsType).toBe(SCOPE_CATEGORY);
    expect(coupon.goodsValue).toEqual([1300]);
    expect(coupon.discountType).toBe(DISCOUNT_PERCENT);
    expect(coupon.discount).toBe(10);
    expect(coupon.discountCap).toBe(5);
    expect(coupon.min).toBe(30);
    expect(coupon.name).toBe("10% off Women's Clothing, cap $5.00, min spend $30.00");
    expect(consume).toEqual({ kind: 'coupon', goodsId: 10008302, day: '2026-08-08' });
  });

  it('maps a goods-scoped suggestion and falls back to the candidate goods id', () => {
    const explicit = couponPrefill(couponCandidate({ scopeType: 'goods', goodsIds: [7, 8], discount: 4 }));
    expect(explicit.coupon.goodsType).toBe(SCOPE_GOODS);
    expect(explicit.coupon.goodsValue).toEqual([7, 8]);
    const fallback = couponPrefill(couponCandidate({ scopeType: 'goods', discount: 4 }));
    expect(fallback.coupon.goodsValue).toEqual([10008302]);
    expect(fallback.coupon.discountType).toBe(0);
  });
});

describe('grouponPrefill', () => {
  it('maps the suggestion onto GrouponRuleForm fields with a windowDays window', () => {
    const now = new Date(2026, 7, 8, 9, 30); // 2026-08-08 09:30 local
    const { combination, consume } = grouponPrefill(
      grouponCandidate({ combinationPrice: 12.99, originalPrice: 25, requiredMembers: 3, limitPerUser: 1, windowDays: 7 }),
      now
    );
    expect(combination.goodsId).toBe(10008302);
    expect(combination.title).toBe('Group buy: Linen Shirt');
    expect(combination.picUrl).toBe('/_cdn/cf/shirt.jpg');
    expect(combination.combinationPrice).toBe(12.99);
    expect(combination.originalPrice).toBe(25);
    expect(combination.requiredMembers).toBe(3);
    expect(combination.limitPerUser).toBe(1);
    expect(combination.startTime).toBe('2026-08-08T09:30');
    expect(combination.endTime).toBe('2026-08-15T09:30');
    expect(consume).toEqual({ kind: 'groupon', goodsId: 10008302, day: '2026-08-08' });
  });

  it('defaults original price to retail and the window to 7 days when unspecified', () => {
    const now = new Date(2026, 0, 1, 0, 0);
    const { combination } = grouponPrefill(grouponCandidate({ combinationPrice: 13.13 }), now);
    expect(combination.originalPrice).toBe(25);
    expect(combination.requiredMembers).toBe(2);
    expect(combination.endTime).toBe('2026-01-08T00:00');
  });
});

describe('createdRefId', () => {
  it('extracts the created coupon/combination id from the operation payload', () => {
    expect(createdRefId({ data: { success: true, data: { couponId: 42 } } }, 'couponId')).toBe(42);
    expect(createdRefId({ data: { success: true, data: { combinationId: 7 } } }, 'combinationId')).toBe(7);
  });

  it('returns undefined when the id is absent or unusable', () => {
    expect(createdRefId({ data: { success: true, data: {} } }, 'couponId')).toBeUndefined();
    expect(createdRefId({ data: { success: true } }, 'couponId')).toBeUndefined();
    expect(createdRefId({ error: { status: 500 } }, 'couponId')).toBeUndefined();
    expect(createdRefId(undefined, 'combinationId')).toBeUndefined();
    expect(createdRefId({ data: { success: true, data: { couponId: 'nope' } } }, 'couponId')).toBeUndefined();
  });
});
