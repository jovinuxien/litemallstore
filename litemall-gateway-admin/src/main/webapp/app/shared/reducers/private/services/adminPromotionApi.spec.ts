import { describe, expect, it } from '@jest/globals';

import {
  couponCommand,
  deliverCommand,
  promotionOpMessage,
  promotionOpWarning,
  toCoupon,
  toCouponPerformance,
  toDeliverResult,
  toDeliveriesPage,
  toDiscountTypeCode,
} from './adminPromotionApi';

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

// ----- Wave 22: RFM delivery + measurement mappers ---------------------------

describe('deliverCommand', () => {
  it('drops unset criteria and sends preview only when true', () => {
    expect(deliverCommand({ couponId: 7, recencyDays: 30, preview: true })).toEqual({ recencyDays: 30, preview: true });
    expect(deliverCommand({ couponId: 7, minFrequency: 2, minMonetary: 50 })).toEqual({ minFrequency: 2, minMonetary: 50 });
  });

  it('an empty segment (every customer) sends an empty body for a real run', () => {
    expect(deliverCommand({ couponId: 7 })).toEqual({});
  });
});

describe('toDeliverResult', () => {
  it('reads counts nested in PromotionOperation.data', () => {
    expect(toDeliverResult({ success: true, data: { matched: 132, granted: 120, skipped: 12 } })).toEqual({
      matched: 132,
      granted: 120,
      skipped: 12,
    });
  });

  it('reads a preview payload (matched only) and bare payloads', () => {
    expect(toDeliverResult({ success: true, data: { matched: 41 } })).toEqual({ matched: 41, granted: undefined, skipped: undefined });
    expect(toDeliverResult({ matched: 5, granted: 5, skipped: 0 })).toEqual({ matched: 5, granted: 5, skipped: 0 });
  });
});

describe('toCouponPerformance', () => {
  it('normalises a bare performance DTO', () => {
    const p = toCouponPerformance({ granted: 120, used: 30, redemptionPct: 25, ordersCount: 30, revenue: 1234.5, avgOrderValue: 41.15 });
    expect(p).toEqual({ granted: 120, used: 30, redemptionPct: 25, ordersCount: 30, revenue: 1234.5, avgOrderValue: 41.15 });
  });

  it('keeps unavailable ratios null (never 0) and tolerates a data wrapper', () => {
    const p = toCouponPerformance({ data: { granted: 0, used: 0, redemptionPct: null, ordersCount: 0, revenue: 0, avgOrderValue: null } });
    expect(p.granted).toBe(0);
    expect(p.redemptionPct).toBeNull();
    expect(p.avgOrderValue).toBeNull();
  });
});

describe('toDeliveriesPage', () => {
  it('normalises the page envelope, delivery ids and array datetimes', () => {
    const page = toDeliveriesPage({
      total: 2,
      pages: 1,
      page: 1,
      limit: 10,
      list: [
        { deliveryId: 3, couponId: 7, segmentJson: '{"recencyDays":30}', matched: 132, granted: 120, skipped: 12, addTime: [2026, 8, 8, 10, 5, 0] },
        { id: 4, couponId: 7, segmentJson: '{}', matched: 9, granted: 9, skipped: 0, addTime: '2026-08-08T11:00:00' },
      ],
    });
    expect(page.total).toBe(2);
    expect(page.list[0].id).toBe(3);
    expect(page.list[0].addTime).toBe('2026-08-08T10:05:00');
    expect(page.list[1].id).toBe(4);
    expect(page.list[1].addTime).toBe('2026-08-08T11:00:00');
  });

  it('accepts a bare array defensively and defaults an empty response', () => {
    const bare = toDeliveriesPage([{ deliveryId: 1, couponId: 7 }]);
    expect(bare.list).toHaveLength(1);
    expect(bare.total).toBe(1);
    expect(toDeliveriesPage(undefined)).toEqual({ list: [], total: 0, page: 1, limit: 0, pages: 0 });
  });
});
