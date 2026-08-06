import { describe, expect, it } from '@jest/globals';

import { couponCommand, promotionOpMessage, promotionOpWarning, toCoupon, toDiscountTypeCode } from './adminPromotionApi';

// Wave 18: wire-shape normalisation for the coupon percent/scope additions.
// The promotion manager DTOs serialize enums as display-name strings; rows
// predating V51 carry no discountType at all.

describe('toDiscountTypeCode', () => {
  it('passes numeric codes through and defaults absent to flat (0)', () => {
    expect(toDiscountTypeCode(1)).toBe(1);
    expect(toDiscountTypeCode(0)).toBe(0);
    expect(toDiscountTypeCode(undefined)).toBe(0);
    expect(toDiscountTypeCode(null)).toBe(0);
  });

  it('accepts display-name strings case-insensitively', () => {
    expect(toDiscountTypeCode('Percent')).toBe(1);
    expect(toDiscountTypeCode('PERCENT')).toBe(1);
    expect(toDiscountTypeCode('Flat')).toBe(0);
    expect(toDiscountTypeCode('anything-else')).toBe(0);
  });
});

describe('toCoupon', () => {
  it('normalises a percent scoped manager DTO', () => {
    const c = toCoupon({
      couponId: 7,
      name: 'W18',
      discount: 10,
      discountType: 'Percent',
      discountCap: 5,
      goodsType: 'Category',
      goodsValue: [1005000],
      type: 'Common',
      status: 'Normal',
      timeType: 'Relative days',
    });
    expect(c.id).toBe(7);
    expect(c.discountType).toBe(1);
    expect(c.discountCap).toBe(5);
    expect(c.goodsType).toBe(1);
    expect(c.goodsValue).toEqual([1005000]);
  });

  it('treats a pre-V51 row (no discountType) as flat', () => {
    expect(toCoupon({ couponId: 1, discount: 5 }).discountType).toBe(0);
  });
});

describe('couponCommand', () => {
  it('always sends discountType (0 survives clean) and the scope fields', () => {
    const body = couponCommand({ name: 'c', discount: 5, goodsType: 2, goodsValue: [10008302, 10008303] });
    expect(body.discountType).toBe(0);
    expect(body.goodsType).toBe(2);
    expect(body.goodsValue).toEqual([10008302, 10008303]);
    expect('discountCap' in body).toBe(false);
  });

  it('sends the cap for a percent coupon and never for a flat one', () => {
    const percent = couponCommand({ name: 'c', discount: 10, discountType: 1, discountCap: 5 });
    expect(percent.discountType).toBe(1);
    expect(percent.discountCap).toBe(5);
    const flatWithStaleCap = couponCommand({ name: 'c', discount: 5, discountType: 0, discountCap: 5 });
    expect('discountCap' in flatWithStaleCap).toBe(false);
  });
});

describe('promotionOpMessage / promotionOpWarning', () => {
  const guardReject = {
    error: {
      status: 400,
      data: { success: false, message: 'Discount exceeds the margin guard: maximum for this scope is $3.75.' },
    },
  };

  it('surfaces the margin-guard rejection message VERBATIM', () => {
    expect(promotionOpMessage(guardReject)).toBe('Discount exceeds the margin guard: maximum for this scope is $3.75.');
    expect(promotionOpWarning(guardReject)).toBeNull();
  });

  it('reports uncostedCount as a non-blocking warning on success', () => {
    const res = { data: { success: true, data: { couponId: 9, uncostedCount: 12 } } };
    expect(promotionOpMessage(res)).toBeNull();
    expect(promotionOpWarning(res)).toMatch(/12 items .*no captured cost/);
  });

  it('stays silent on a clean success', () => {
    expect(promotionOpWarning({ data: { success: true, data: { couponId: 9, uncostedCount: 0 } } })).toBeNull();
    expect(promotionOpWarning({ data: { success: true } })).toBeNull();
  });
});
