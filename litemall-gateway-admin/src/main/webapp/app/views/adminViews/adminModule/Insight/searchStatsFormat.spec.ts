import { describe, expect, it } from '@jest/globals';

import { toSearchStats } from 'app/shared/reducers/private/services/insightApi';
import { ctrOf, fmtCount, fmtCtr, fmtZeroShare, overallCtrPct, summaryText } from './searchStatsFormat';

// Wave 22: search-demand analytics render helpers + the search-stats
// envelope normalisation.

describe('ctrOf', () => {
  it('prefers the server-computed ctrPct', () => {
    expect(ctrOf({ keyword: 'shirt', searches: 100, clicks: 10, ctrPct: 12.5 })).toBe(12.5);
  });

  it('derives clicks/searches when ctrPct is absent', () => {
    expect(ctrOf({ keyword: 'shirt', searches: 200, clicks: 30 })).toBeCloseTo(15);
  });

  it('is null (rendered as a dash) with zero searches', () => {
    expect(ctrOf({ keyword: 'x', searches: 0, clicks: 0 })).toBeNull();
    expect(ctrOf({ keyword: 'x' })).toBeNull();
    expect(fmtCtr(null)).toBe('—');
  });
});

describe('overallCtrPct', () => {
  it('derives the window CTR from totals', () => {
    expect(overallCtrPct({ searches: 1000, clicks: 125 })).toBeCloseTo(12.5);
    expect(fmtCtr(overallCtrPct({ searches: 1000, clicks: 125 }))).toBe('12.5%');
  });

  it('is null when nothing was searched', () => {
    expect(overallCtrPct({ searches: 0, clicks: 0 })).toBeNull();
    expect(overallCtrPct(undefined)).toBeNull();
  });
});

describe('fmtCount / fmtZeroShare', () => {
  it('renders counts with grouping and dashes for absent values', () => {
    expect(fmtCount(12824)).toBe('12,824');
    expect(fmtCount(0)).toBe('0');
    expect(fmtCount(undefined)).toBe('—');
  });

  it('renders the zero-result share of searches', () => {
    expect(fmtZeroShare({ searches: 1000, zeroResults: 31, clicks: 0 })).toBe('31 (3.1%)');
    expect(fmtZeroShare({ searches: 0, zeroResults: 4 })).toBe('4');
    expect(fmtZeroShare(undefined)).toBe('—');
  });
});

describe('summaryText', () => {
  it('passes strings and message fields through verbatim', () => {
    expect(summaryText('12 trending keywords set (3 curated preserved)', 'x')).toBe('12 trending keywords set (3 curated preserved)');
    expect(summaryText({ message: 'Refreshed 12 keywords.' }, 'x')).toBe('Refreshed 12 keywords.');
  });

  it('renders flat scalar/string-array summaries honestly', () => {
    expect(summaryText({ added: 5, kept: 7, keywords: ['tent', 'mug'] }, 'x')).toBe('added: 5 · kept: 7 · keywords: tent, mug');
  });

  it('falls back when there is nothing renderable', () => {
    expect(summaryText(undefined, 'Trending keywords refreshed.')).toBe('Trending keywords refreshed.');
    expect(summaryText({}, 'Rollup completed.')).toBe('Rollup completed.');
    expect(summaryText({ nested: { a: 1 } }, 'Rollup completed.')).toBe('Rollup completed.');
  });
});

describe('toSearchStats', () => {
  it('defaults every section so the panel always renders', () => {
    expect(toSearchStats(undefined)).toEqual({ topQueries: [], zeroResultQueries: [], totals: {} });
    expect(toSearchStats(null)).toEqual({ topQueries: [], zeroResultQueries: [], totals: {} });
  });

  it('keeps served sections as-is', () => {
    const served = {
      topQueries: [{ keyword: 'shirt', searches: 10, zeroResults: 0, clicks: 4, ctrPct: 40 }],
      zeroResultQueries: [{ keyword: 'kayak', searches: 3, zeroResults: 3, clicks: 0, ctrPct: 0 }],
      totals: { searches: 13, zeroResults: 3, clicks: 4 },
    };
    expect(toSearchStats(served)).toEqual(served);
  });
});
