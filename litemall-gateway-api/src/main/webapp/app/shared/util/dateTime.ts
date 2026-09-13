/**
 * Order-service timestamps arrive in two shapes: an ISO-8601 string, or — on rows that
 * go through the shared litemall-db mapper — a LocalDateTime tuple `[y, m, d, h, min, …]`.
 * Render both as `YYYY-MM-DD HH:mm`; anything else renders empty rather than "Invalid".
 */
export const fmtDateTime = (t?: string | number[] | null): string => {
  if (Array.isArray(t) && t.length >= 5) {
    const [y, m, d, h, min] = t;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`;
  }
  return typeof t === 'string' ? t.slice(0, 16).replace('T', ' ') : '';
};
