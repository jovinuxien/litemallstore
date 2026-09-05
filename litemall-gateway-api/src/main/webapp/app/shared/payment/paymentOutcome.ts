import { IOrderDetail } from 'app/shared/model/order/order.model';

/**
 * Payment-outcome helpers for the order lifecycle contract
 * (litemall-order/docs/handoff-gateway-api-lifecycle.md §1).
 *
 * The order service answers a few payment situations with a typed 402 whose MESSAGE is the
 * contract (there is no errorCode on `payment-intent` refusals). Two of them are not
 * failures at all and must not be rendered as one:
 *  - "has already been paid" — the webhook (or another tab, or a redirect the SPA never
 *    followed) settled the order first. That is success: reload the order.
 *  - "still being processed by your bank" — a SEPA/bank-debit intent is in flight for days.
 *    The customer must NOT be offered a retry; the backend refuses to mint a second intent.
 * Everything else is a genuine refusal shown verbatim.
 */
export type IntentRefusal = 'already-paid' | 'processing' | 'other';

export const classifyIntentRefusal = (message: string | null | undefined): IntentRefusal => {
  const m = (message ?? '').toLowerCase();
  if (m.includes('already been paid')) return 'already-paid';
  if (m.includes('still being processed')) return 'processing';
  return 'other';
};

/**
 * What the pay-status page can tell from the URL it was opened with. Stripe's redirect
 * return (Klarna, iDEAL, Bancontact, 3-DS) appends `redirect_status`; our own navigations
 * pass `status` (the wallet pay page and the checkout's processing hand-off).
 *
 * Only `failed` is a terminal answer here. `succeeded` is NOT taken on trust — the order
 * service marks the order paid from the webhook after re-verifying the intent, so the page
 * still polls the order for the real state.
 */
export type RedirectOutcome = 'failed' | 'processing' | 'pending';

export const redirectOutcome = (params: URLSearchParams): RedirectOutcome => {
  const redirect = params.get('redirect_status');
  const own = params.get('status') ?? params.get('result');
  if (redirect === 'failed' || own === 'fail' || own === 'failed' || own === 'cancel') return 'failed';
  if (redirect === 'processing' || own === 'processing') return 'processing';
  return 'pending';
};

/**
 * Settlement state read off the customer detail payload. The customer DTO carries no
 * numeric orderStatus; `handleOption.pay` is true exactly while the order is CREATED
 * (unpaid), and a cancelled order says so in its status text. Anything else that can no
 * longer be paid has been paid.
 */
export type Settlement = 'unpaid' | 'paid' | 'cancelled' | 'unknown';

export const settlementOf = (detail: IOrderDetail | null | undefined): Settlement => {
  const opt = detail?.handleOption;
  if (!opt) return 'unknown';
  if (opt.pay) return 'unpaid';
  if (/cancel/i.test(detail?.orderStatusText ?? '')) return 'cancelled';
  return 'paid';
};
