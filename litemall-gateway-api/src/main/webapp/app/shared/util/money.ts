import { currentLocaleTag } from 'app/i18n/locale';
import { CHARGE_CURRENCY, type Currency, currentCurrency, currentRate, isConverted } from 'app/shared/util/currency';

/**
 * Storefront money formatter — the ONE place that knows how a price is written.
 *
 * Amounts arrive from the server as plain EURO decimals; the store prices and CHARGES
 * EUR (Wave 24). Two families of formatter, and choosing between them is the whole
 * design (docs/spec-display-currency.md):
 *
 *  - `money` / `moneyShort` / `moneyParts` write a price in the visitor's DISPLAY
 *    currency (shared/util/currency.ts): converted with the ECB reference rate and
 *    rounded to a shop price. Use for anything a shopper browses — cards, product
 *    pages, cart lines, coupons, delivery options.
 *  - `moneyCharged` always writes the euro amount, to the cent. Use for anything that
 *    is, or records, a CHARGE — the amount to pay, order history, refunds.
 *
 * A converted price is indicative: the bank debits euros at its own rate. So wherever
 * a total is about to be paid, the euro figure is shown beside it (`chargedNote`).
 * When the display currency is EUR — the default, and whenever rates are missing —
 * both families write exactly the same thing and nothing is converted.
 *
 * Rounding: kronor (SEK/DKK/NOK) to whole units, pounds and euros to 2 decimals. A
 * positive amount never rounds down to zero.
 *
 * Writing: euros follow the active language ("€1,234.56" / "1 234,56 €" /
 * "1.234,56 €"). Kronor are always "<amount> kr" and pounds "£<amount>", with the
 * active language's separators — Intl would write "SEK 139" in English, which no
 * shopper in Sweden reads as a price.
 */

/** Currency the store charges (ISO 4217). Stripe Elements and analytics read this. */
export const CURRENCY = CHARGE_CURRENCY;

const DECIMALS: Record<Currency, number> = { EUR: 2, GBP: 2, SEK: 0, DKK: 0, NOK: 0 };

const formatters = new Map<string, Intl.NumberFormat>();
const formatterFor = (tag: string, kind: 'eur' | number): Intl.NumberFormat => {
  const key = `${tag}|${kind}`;
  let f = formatters.get(key);
  if (!f) {
    const digits = kind === 'eur' ? 2 : kind;
    const base = { minimumFractionDigits: digits, maximumFractionDigits: digits };
    try {
      f = new Intl.NumberFormat(tag, kind === 'eur' ? { style: 'currency', currency: 'EUR', ...base } : base);
    } catch {
      f = new Intl.NumberFormat('en-IE', base);
    }
    formatters.set(key, f);
  }
  return f;
};

const safe = (v: number | string | null | undefined): number => {
  const n = Number(v ?? 0);
  return Number.isFinite(n) ? n : 0;
};

const roundTo = (n: number, digits: number): number => {
  const p = 10 ** digits;
  // toPrecision first: 1.005 * 100 is 100.49999… in binary and would round down.
  return Math.round(Number((n * p).toPrecision(12))) / p;
};

/**
 * Euro amount → the number written in the display currency (converted + rounded).
 * Identity for EUR. Exported for tests and for renders that need the bare number.
 */
export const toDisplayAmount = (v: number | string | null | undefined): number => {
  const eur = safe(v);
  const cur = currentCurrency();
  if (cur === CHARGE_CURRENCY) return eur;
  const digits = DECIMALS[cur];
  const rounded = roundTo(eur * currentRate(), digits);
  // A real price must not read as free (or a real discount as nothing).
  if (rounded === 0 && eur !== 0) return Math.sign(eur) / 10 ** digits;
  return rounded;
};

const writeDisplay = (amount: number, digits: number): string => {
  const cur = currentCurrency();
  const tag = currentLocaleTag();
  if (cur === CHARGE_CURRENCY) return formatterFor(tag, digits === 2 ? 'eur' : digits).format(amount);
  const n = formatterFor(tag, digits).format(amount);
  return cur === 'GBP' ? `£${n}` : `${n} kr`;
};

/** A price in the display currency: "€12.34" · "139 kr" · "£10.69". NaN/undefined-safe. */
export const money = (v: number | string | null | undefined): string => writeDisplay(toDisplayAmount(v), DECIMALS[currentCurrency()]);

/**
 * Like `money`, but a whole amount drops its decimals ("€5", "£5", "55 kr") — for
 * coupon values and thresholds, which are written as round figures.
 */
export const moneyShort = (v: number | string | null | undefined): string => {
  const amount = toDisplayAmount(v);
  const cur = currentCurrency();
  if (!Number.isInteger(amount)) return writeDisplay(amount, DECIMALS[cur]);
  if (cur !== CHARGE_CURRENCY) return writeDisplay(amount, 0);
  // Whole euros: keep the language's own symbol placement, without ",00".
  const tag = currentLocaleTag();
  const key = `${tag}|eur0`;
  let f = formatters.get(key);
  if (!f) {
    try {
      f = new Intl.NumberFormat(tag, { style: 'currency', currency: 'EUR', minimumFractionDigits: 0, maximumFractionDigits: 0 });
    } catch {
      f = new Intl.NumberFormat('en-IE', { style: 'currency', currency: 'EUR', minimumFractionDigits: 0, maximumFractionDigits: 0 });
    }
    formatters.set(key, f);
  }
  return f.format(amount);
};

/** The EURO amount, to the cent, in the active language's writing — what is actually charged. */
export const moneyCharged = (v: number | string | null | undefined): string => formatterFor(currentLocaleTag(), 'eur').format(safe(v));

/**
 * The euro amount to show BESIDE a converted total, or null when prices on screen
 * already are the charged euro amounts (nothing to add).
 */
export const chargedNote = (v: number | string | null | undefined): string | null => (isConverted() ? moneyCharged(v) : null);

/**
 * Split for the Amazon-style card price: symbol, big integer part, superscript minor
 * units. Grouping is omitted on purpose (the card's big-int style). Kronor have no
 * minor part (`cents` is ''), and their symbol trails the amount.
 */
export const moneyParts = (v: number | string | null | undefined): { before: string; int: string; cents: string; after: string } => {
  const cur = currentCurrency();
  const digits = DECIMALS[cur];
  const [int, cents = ''] = toDisplayAmount(v).toFixed(digits).split('.');
  if (cur === 'EUR') return { before: '€', int, cents, after: '' };
  if (cur === 'GBP') return { before: '£', int, cents, after: '' };
  return { before: '', int, cents, after: 'kr' };
};
