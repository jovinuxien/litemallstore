import { describe, expect, it } from '@jest/globals';

import { PAGE_CATEGORIES, categoryLabel, categoryTag, clonedPageId, normalizeCategory } from './pageFormat';

// Wave 20: DIY-page category/template helpers behind the PageList filters,
// the PageEditor category select and the clone ("Use template"/"Duplicate")
// navigation.

describe('normalizeCategory', () => {
  it('passes the three known categories through', () => {
    expect(normalizeCategory('general')).toBe('general');
    expect(normalizeCategory('coupon')).toBe('coupon');
    expect(normalizeCategory('groupon')).toBe('groupon');
  });

  it('maps absent/unknown values to general (pre-V54 rows carry no category)', () => {
    expect(normalizeCategory(undefined)).toBe('general');
    expect(normalizeCategory(null)).toBe('general');
    expect(normalizeCategory('')).toBe('general');
    expect(normalizeCategory('seckill')).toBe('general');
  });
});

describe('categoryLabel / categoryTag', () => {
  it('labels every declared category', () => {
    for (const c of PAGE_CATEGORIES) {
      expect(categoryLabel(c.value)).toBe(c.label);
    }
  });

  it('falls back to General for unknown input', () => {
    expect(categoryLabel(undefined)).toBe('General');
    expect(categoryLabel('bogus')).toBe('General');
  });

  it('colors coupon/groupon distinctly and keeps general neutral', () => {
    expect(categoryTag('coupon')).toBe('danger');
    expect(categoryTag('groupon')).toBe('warning');
    expect(categoryTag('general')).toBe('info');
    expect(categoryTag(undefined)).toBe('info');
  });
});

describe('clonedPageId', () => {
  it('returns the new page id from a successful clone envelope', () => {
    expect(clonedPageId({ errno: 0, errmsg: 'ok', data: { id: 42, name: 'Copy of Coupon spotlight' } })).toBe(42);
  });

  it('returns null on a business error (caller shows errmsg instead)', () => {
    expect(clonedPageId({ errno: 641, errmsg: 'conflict' })).toBeNull();
  });

  it('returns null when the row or id is missing/malformed', () => {
    expect(clonedPageId({ errno: 0, errmsg: 'ok' })).toBeNull();
    expect(clonedPageId({ errno: 0, errmsg: 'ok', data: { id: 'x' } })).toBeNull();
    expect(clonedPageId(undefined)).toBeNull();
    expect(clonedPageId(null)).toBeNull();
  });
});
