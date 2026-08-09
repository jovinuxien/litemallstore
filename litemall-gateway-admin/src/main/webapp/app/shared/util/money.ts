// Shared admin money formatter. The store prices and charges EUR storewide
// (Wave 24; Stripe charges LITEMALL_ORDER_STRIPE_CURRENCY=eur); backend amounts
// are plain currency-less decimals, so this is purely a display concern.
// Pre-flip order history is accepted as mixed-currency and renders as plain
// numbers with the € symbol — no per-row currency logic.
export const money = (v?: number | string | null): string => (v == null || v === '' ? '—' : `€${Number(v).toFixed(2)}`);
