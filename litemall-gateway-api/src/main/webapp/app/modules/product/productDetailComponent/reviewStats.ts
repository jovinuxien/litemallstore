import { IComment } from 'app/shared/api';

/**
 * Client-side review aggregates for the Amazon-style summary block. Computed
 * over the comments actually fetched (the PDP loads reviews in pages of 100;
 * CJ ingest caps at ~60 per product, so this is normally the complete set).
 */
export interface ReviewStats {
  average: number;
  /** Rows 5★ → 1★, pct = integer share of the counted reviews. */
  bars: { star: number; count: number; pct: number }[];
  counted: number;
}

const clampStar = (s: unknown): number | null => {
  const n = Number(s);
  if (!Number.isFinite(n)) return null;
  return Math.max(1, Math.min(5, Math.round(n)));
};

export const computeReviewStats = (comments: IComment[]): ReviewStats => {
  const counts = [0, 0, 0, 0, 0, 0]; // index = star 1..5
  let sum = 0;
  let counted = 0;
  for (const c of comments ?? []) {
    const star = clampStar(c?.star);
    if (star == null) continue;
    counts[star] += 1;
    sum += star;
    counted += 1;
  }
  const bars = [5, 4, 3, 2, 1].map(star => ({
    star,
    count: counts[star],
    pct: counted ? Math.round((counts[star] / counted) * 100) : 0,
  }));
  return { average: counted ? sum / counted : 0, bars, counted };
};
