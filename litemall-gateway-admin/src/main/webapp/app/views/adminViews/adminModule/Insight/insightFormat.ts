// Wave 12: shared formatters for the CJ inventory-insight views. Margin/cost
// fields are `null` while the goods' CJ cost is not captured yet (Wave-12
// CONTRACT: null, never 0) — render those as an explicit "—".

export const fmtMoney = (v?: number | null): string => (v == null ? '—' : `$${Number(v).toFixed(2)}`);

export const fmtPct = (v?: number | null): string => (v == null ? '—' : `${Number(v).toFixed(1)}%`);

export const fmtInt = (v?: number | null): string => (v == null ? '—' : String(v));

// ISO LocalDateTime "2026-07-28T18:00:00" → "2026-07-28 18:00" for tables.
export const fmtDateTime = (v?: string): string => (v ? v.replace('T', ' ').slice(0, 16) : '—');

// Arrival dates arrive as "2026-07-28" or a full LocalDateTime — keep the day.
export const fmtDay = (v?: string): string => (v ? v.slice(0, 10) : '—');
