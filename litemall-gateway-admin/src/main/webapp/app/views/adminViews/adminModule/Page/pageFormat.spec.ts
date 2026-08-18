import { describe, expect, it } from '@jest/globals';

import { PAGE_CATEGORIES, categoryLabel, categoryTag, clonedPageId, normalizeCategory } from './pageFormat';

// Wave 20: DIY-page category/template helpers behind the PageList filters,
// the PageEditor category select and the clone ("Use template"/"Duplicate")
// navigation. Wave 27 adds 'season' (spec-season-collection.md) — the filter
// option lists now render from PAGE_CATEGORIES, so this suite guards them all.

describe('normalizeCategory', () => {
  it('passes the four known categories through', () => {
    expect(normalizeCategory('general')).toBe('general');
    expect(normalizeCategory('coupon')).toBe('coupon');
    expect(normalizeCategory('groupon')).toBe('groupon');
    expect(normalizeCategory('season')).toBe('season');
  });

  // Wave 27 regression pin. PageEditor seeds its category select from this
  // helper, so a 'season' row folded into 'general' would be silently rewritten
  // by a plain open-and-save and orphaned from GET /srv/page/season. V63 seeds
  // a real season template, so this path is reachable the moment it regresses.
  it('round-trips season so opening and saving a season page cannot rewrite it', () => {
    for (const c of PAGE_CATEGORIES) {
      expect(normalizeCategory(c.value)).toBe(c.value);
    }
  });

  it('maps absent/unknown values to general (pre-V54 rows carry no category)', () => {
    expect(normalizeCategory(undefined)).toBe('general');
    expect(normalizeCategory(null)).toBe('general');
    expect(normalizeCategory('')).toBe('general');
    expect(normalizeCategory('seckill')).toBe('general');
    expect(normalizeCategory('Season')).toBe('general'); // case-sensitive: the server value is lowercase
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

  it('colors coupon/groupon/season distinctly and keeps general neutral', () => {
    expect(categoryTag('coupon')).toBe('danger');
    expect(categoryTag('groupon')).toBe('warning');
    expect(categoryTag('season')).toBe('success');
    expect(categoryTag('general')).toBe('info');
    expect(categoryTag(undefined)).toBe('info');
  });

  it('declares season so the PageList/Postiz filters and the editor select offer it', () => {
    expect(PAGE_CATEGORIES.map(c => c.value)).toContain('season');
    expect(categoryLabel('season')).toBe('Season');
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
