import { ISearchQueryStat, ISearchStatsTotals } from 'app/shared/reducers/private/services/insightApi';

// Wave 22: pure render helpers for the search-demand analytics panel
// (/srv/private/admin/insight/search-stats). Kept view-free for unit tests.
// Money-free surface — CTRs and counts only.

/** Row CTR %: prefer the server-computed ctrPct; derive clicks/searches when
 *  it is absent; null (rendered "—") when there were no searches. */
export const ctrOf = (row: ISearchQueryStat): number | null => {
  if (row.ctrPct != null) return Number(row.ctrPct);
  const searches = Number(row.searches ?? 0);
  if (!(searches > 0)) return null;
  return (Number(row.clicks ?? 0) / searches) * 100;
};

/** Overall CTR % across the window's totals; null when nothing was searched. */
export const overallCtrPct = (totals?: ISearchStatsTotals | null): number | null => {
  const searches = Number(totals?.searches ?? 0);
  if (!(searches > 0)) return null;
  return (Number(totals?.clicks ?? 0) / searches) * 100;
};

export const fmtCtr = (v: number | null): string => (v == null ? '—' : `${v.toFixed(1)}%`);

export const fmtCount = (v?: number | null): string => (v == null ? '—' : Number(v).toLocaleString('en-US'));

/** Zero-result share of a window's searches, e.g. "42 (3.1%)". */
export const fmtZeroShare = (totals?: ISearchStatsTotals | null): string => {
  const zero = totals?.zeroResults;
  if (zero == null) return '—';
  const searches = Number(totals?.searches ?? 0);
  if (!(searches > 0)) return fmtCount(zero);
  return `${fmtCount(zero)} (${((Number(zero) / searches) * 100).toFixed(1)}%)`;
};

/** Pretty-print an operation summary (trending refresh / rollup run). The
 *  contract fixes only "→ summary", so accept a string, a {message}, or any
 *  flat object of scalar/string-array values — rendered honestly, never
 *  invented. */
export const summaryText = (data: unknown, fallback: string): string => {
  if (data == null) return fallback;
  if (typeof data === 'string') return data.trim() || fallback;
  if (typeof data !== 'object') return String(data);
  const obj = data as Record<string, unknown>;
  if (typeof obj.message === 'string' && obj.message.trim()) return obj.message;
  const parts = Object.entries(obj)
    .filter(([, v]) => v != null)
    .map(([k, v]) => {
      if (Array.isArray(v)) return v.every(x => typeof x === 'string' || typeof x === 'number') ? `${k}: ${v.join(', ')}` : null;
      if (typeof v === 'object') return null;
      return `${k}: ${String(v)}`;
    })
    .filter((p): p is string => p != null);
  return parts.length > 0 ? parts.join(' · ') : fallback;
};
