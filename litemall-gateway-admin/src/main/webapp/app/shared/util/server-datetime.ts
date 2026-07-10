// Some backend surfaces (promotion-service manager DTOs, goods-management
// engagement rows) serialize Java LocalDateTime as a numeric array
// [y, m, d, h, min, s?, nanos?] instead of an ISO string, depending on the
// module's Jackson setup. Normalise both to 'YYYY-MM-DDTHH:mm:ss' so views
// can keep the plain-string rendering (`v.replace('T', ' ').slice(...)`).
export const fromServerDateTime = (v: unknown): string | undefined => {
  if (typeof v === 'string' && v) return v;
  if (Array.isArray(v) && v.length >= 3) {
    const [y, mo, d, h = 0, mi = 0, s = 0] = v as number[];
    const p = (n: number) => String(n).padStart(2, '0');
    return `${y}-${p(mo)}-${p(d)}T${p(h)}:${p(mi)}:${p(s)}`;
  }
  return undefined;
};
