import { money } from 'app/shared/util/money';

// Wave 12: shared formatters for the CJ inventory-insight views. Margin/cost
// fields are `null` while the goods' CJ cost is not captured yet (Wave-12
// CONTRACT: null, never 0) — render those as an explicit "—".

export const fmtMoney = (v?: number | null): string => money(v);

export const fmtPct = (v?: number | null): string => (v == null ? '—' : `${Number(v).toFixed(1)}%`);

export const fmtInt = (v?: number | null): string => (v == null ? '—' : String(v));

// ISO LocalDateTime "2026-07-28T18:00:00" → "2026-07-28 18:00" for tables.
export const fmtDateTime = (v?: string): string => (v ? v.replace('T', ' ').slice(0, 16) : '—');

// Arrival dates arrive as "2026-07-28" or a full LocalDateTime — keep the day.
export const fmtDay = (v?: string): string => (v ? v.slice(0, 10) : '—');

// Wave 14: default retirement date = the next STRICTLY future Wednesday (the
// 02:00 executor has already run on the current day), as a local 'YYYY-MM-DD'.
export const nextWednesday = (from: Date = new Date()): string => {
  const d = new Date(from);
  d.setDate(d.getDate() + ((3 - d.getDay() + 7) % 7 || 7));
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};
