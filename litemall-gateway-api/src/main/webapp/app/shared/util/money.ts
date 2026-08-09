/**
 * Wave-24 storefront money formatter — the ONE place that knows the display
 * currency. The store prices and charges EUR (single currency, storewide);
 * amounts arrive from the server as plain decimals and are formatted here
 * only. Pre-flip order history renders through the same formatter by design
 * (mixed-currency history is accepted, no per-row currency logic).
 */

/** Display currency symbol for every customer-facing money render. */
export const EURO = '€';

const fmt2 = (n: number): string =>
  n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** "€1,234.56" — 2dp, grouped. NaN/undefined-safe (renders €0.00). */
export const money = (v: number | string | null | undefined): string => {
  const n = Number(v ?? 0);
  return `${EURO}${fmt2(Number.isFinite(n) ? n : 0)}`;
};

/** Amount without the symbol ("1,234.56") for renders that place € themselves. */
export const moneyAmount = (v: number | string | null | undefined): string => {
  const n = Number(v ?? 0);
  return fmt2(Number.isFinite(n) ? n : 0);
};

/**
 * Split for the Amazon-style card price: big integer part, superscript cents.
 * toFixed keeps the split locale-stable; grouping is omitted on purpose (the
 * card's big-int style, unchanged from the $ version).
 */
export const moneyParts = (v: number | string | null | undefined): { int: string; cents: string } => {
  const n = Number(v ?? 0);
  const [int, cents] = (Number.isFinite(n) ? n : 0).toFixed(2).split('.');
  return { int, cents };
};
