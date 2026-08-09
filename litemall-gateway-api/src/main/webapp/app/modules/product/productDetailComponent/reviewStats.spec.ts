import { computeReviewStats } from './reviewStats';

describe('computeReviewStats', () => {
  it('computes the average and 5→1 histogram shares', () => {
    const stats = computeReviewStats([{ star: 5 }, { star: 5 }, { star: 4 }, { star: 1 }] as any);
    expect(stats.counted).toBe(4);
    expect(stats.average).toBeCloseTo(3.75);
    expect(stats.bars).toEqual([
      { star: 5, count: 2, pct: 50 },
      { star: 4, count: 1, pct: 25 },
      { star: 3, count: 0, pct: 0 },
      { star: 2, count: 0, pct: 0 },
      { star: 1, count: 1, pct: 25 },
    ]);
  });

  it('clamps out-of-range stars and skips junk', () => {
    const stats = computeReviewStats([{ star: 7 }, { star: 0 }, { star: 'x' }, {}] as any);
    expect(stats.counted).toBe(2); // 7→5, 0→1; 'x' and missing skipped
    expect(stats.bars.find(b => b.star === 5)?.count).toBe(1);
    expect(stats.bars.find(b => b.star === 1)?.count).toBe(1);
  });

  it('is all-zero on an empty list (no NaN percentages)', () => {
    const stats = computeReviewStats([]);
    expect(stats.average).toBe(0);
    expect(stats.counted).toBe(0);
    stats.bars.forEach(b => expect(b.pct).toBe(0));
  });
});
