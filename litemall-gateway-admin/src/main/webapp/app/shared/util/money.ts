// Shared admin money formatter. The store transacts in USD only (Stripe charges
// LITEMALL_ORDER_STRIPE_CURRENCY, default usd); backend amounts are plain
// currency-less decimals, so this is purely a display concern.
export const money = (v?: number | string | null): string => (v == null || v === '' ? '—' : `$${Number(v).toFixed(2)}`);
