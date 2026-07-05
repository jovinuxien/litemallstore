import { IFreightQuote, IOrderDetail, IOrderListItem } from 'app/shared/model/order/order.model';

import { baseAxios, SRV, unwrap } from './http';

/**
 * Order lifecycle against the order-service (`/srv/order`). `submit` exists; the
 * list/detail/ops verbs are agreed `/srv` contracts the `order` worktree still
 * owes (docs/SRV-FOLLOWUPS.md). Views tolerate their absence with empty/error
 * states. No `/wx`.
 */
export interface OrderListParams {
  showType?: number; // 0 all, 1 unpaid, 2 unshipped, 3 shipped, 4 unrated
  page?: number;
  limit?: number;
}

export const orderApi = {
  /** POST /srv/order/submit — place an order. */
  submit: (body: unknown) => unwrap(baseAxios.post(`${SRV}/order/submit`, body)),
  /**
   * POST /srv/order/freight-quote — the freight submit will charge for a cart group's
   * subtotal, plus (CJ carts) the informational logistics line/delivery estimate.
   */
  freightQuote: (body: { countryCode?: string; subtotal: number; cjItems?: { productId?: number; quantity?: number }[] }) =>
    unwrap<IFreightQuote>(baseAxios.post(`${SRV}/order/freight-quote`, body)),
  /**
   * POST /srv/order/{id}/actions/pay — pay a placed order (CARD/WALLET).
   * Returns the OrderOperationDtoResponse verbatim (no errno envelope); a payment
   * failure surfaces as a non-2xx (402) the caller catches. For a source='cj' order
   * the CJ placement runs inside the pay call (3-10s typical) — orderSlice.payOrder
   * owns the long per-request timeout; this helper keeps the 5s global default.
   */
  pay: (orderId: number | string, body: { paymentMethod: string; paymentIntentId?: string }) =>
    unwrap(baseAxios.post(`${SRV}/order/${orderId}/actions/pay`, body)),
  // TODO(/srv follow-up: order) — list/detail/ops not implemented yet.
  list: (params: OrderListParams) => unwrap<{ list: IOrderListItem[]; total: number }>(baseAxios.get(`${SRV}/order/list`, { params })),
  detail: (orderId: number | string) => unwrap<IOrderDetail>(baseAxios.get(`${SRV}/order/detail?orderId=${encodeURIComponent(String(orderId))}`)),
  cancel: (orderId: number | string) => unwrap(baseAxios.post(`${SRV}/order/${orderId}/actions/cancel`, {})),
  confirm: (orderId: number | string) => unwrap(baseAxios.post(`${SRV}/order/${orderId}/actions/confirm`, {})),
  refund: (orderId: number | string) => unwrap(baseAxios.post(`${SRV}/order/${orderId}/actions/refund`, {})),
  remove: (orderId: number | string) => unwrap(baseAxios.post(`${SRV}/order/${orderId}/actions/delete`, {})),
  prepay: (orderId: number | string) => unwrap(baseAxios.post(`${SRV}/order/prepay`, { orderId })),
};
