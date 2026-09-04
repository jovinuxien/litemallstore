import { currentLocaleTag } from 'app/i18n/locale';

/**
 * Wave-24 storefront money formatter — the ONE place that knows the display
 * currency. The store prices and charges EUR (single currency, storewide);
 * amounts arrive from the server as plain decimals and are formatted here
 * only. Pre-flip order history renders through the same formatter by design
 * (mixed-currency history is accepted, no per-row currency logic).
 *
 * i18n foundation: the SAME euro amount is written the way the active language
 * writes it — "€1,234.56" in English, "1 234,56 €" in Swedish, "1.234,56 €" in
 * Danish. This is presentation of one currency, NOT conversion: a Swedish
 * shopper is charged the euro amount shown, to the cent, and their card
 * statement says EUR. Showing kronor here would mean promising an amount we do
 * not charge (see docs/spec-i18n-foundation.md, "Currency").
 */

/** Display currency (ISO 4217). Single currency storewide since Wave 24. */
export const CURRENCY = 'EUR';

/** Display currency symbol for renders that compose it themselves. */
export const EURO = '€';

const formatters = new Map<string, Intl.NumberFormat>();
const formatterFor = (tag: string, style: 'currency' | 'decimal'): Intl.NumberFormat => {
  const key = `${tag}|${style}`;
  let f = formatters.get(key);
  if (!f) {
    try {
      f = new Intl.NumberFormat(
        tag,
        style === 'currency'
          ? { style: 'currency', currency: CURRENCY, minimumFractionDigits: 2, maximumFractionDigits: 2 }
          : { minimumFractionDigits: 2, maximumFractionDigits: 2 }
      );
    } catch {
      f = new Intl.NumberFormat('en-IE', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    formatters.set(key, f);
  }
  return f;
};

const safe = (v: number | string | null | undefined): number => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

/** "€1,234.56" (en) / "1 234,56 €" (sv) / "1.234,56 €" (da) — 2dp, grouped. NaN/undefined-safe. */
export const money = (v: number | string | null | undefined): string => formatterFor(currentLocaleTag(), 'currency').format(safe(v));

/** Amount without the symbol ("1,234.56" / "1 234,56") for renders that place € themselves. */
export const moneyAmount = (v: number | string | null | undefined): string => formatterFor(currentLocaleTag(), 'decimal').format(safe(v));

/**
 * Split for the Amazon-style card price: big integer part, superscript cents.
 * toFixed keeps the split locale-stable; grouping is omitted on purpose (the
 * card's big-int style, unchanged from the $ version). Deliberately NOT
 * localised: it feeds a visual layout, not prose.
 */
export const moneyParts = (v: number | string | null | undefined): { int: string; cents: string } => {
  const [int, cents] = safe(v).toFixed(2).split('.');
  return { int, cents };
};
