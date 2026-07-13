import {
  IAftersale,
  IDispute,
  IDisputeContext,
  IFreightQuote,
  IOrderDetail,
  IOrderListItem,
  IStore,
  ITracking,
} from 'app/shared/model/order/order.model';

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
   * Wave 4 (docs/handoff-gateway-api-freight-quote.md): `addressId` (owner-scoped,
   * resolves the province for template region matching) and `items` (cart lines —
   * REQUIRED for template pricing) are optional — the pre-template backend ignores
   * them, so this is safe either side of the order-service merge.
   */
  freightQuote: (body: {
    countryCode?: string;
    subtotal: number;
    addressId?: number;
    items?: { goodsId?: number | string; quantity?: number; price?: number }[];
    cjItems?: { productId?: number; quantity?: number }[];
  }) => unwrap<IFreightQuote>(baseAxios.post(`${SRV}/order/freight-quote`, body)),
  /**
   * GET /srv/order/{id}/tracking — carrier + tracking number + event list
   * (owner-scoped; contract: litemall-order/docs/handoff-gateway-admin-cj-tracking.md,
   * customer surface). Not shipped yet → {shipped:false}, NOT an error. Slow-ish
   * (live CJ trackInfo behind a 1h server cache) — generous timeout.
   */
  tracking: (orderId: number | string) => unwrap<ITracking>(baseAxios.get(`${SRV}/order/${orderId}/tracking`, { timeout: 30000 })),
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

  // Aftersale / RMA (Wave-2 vertical, live on master —
  // litemall-order/docs/aftersale-vertical.md). errno 730 = rule violation
  // (window closed, open application exists, amount > paid...).
  aftersaleList: (orderId: number | string) => unwrap<IAftersale[]>(baseAxios.get(`${SRV}/order/${orderId}/aftersale`)),
  aftersaleApply: (
    orderId: number | string,
    body: { type: number; reason: string; amount?: number; pictures?: string[]; comment?: string }
  ) => unwrap<IAftersale>(baseAxios.post(`${SRV}/order/${orderId}/aftersale`, body)),
  aftersaleCancel: (orderId: number | string, aftersaleId: number) =>
    unwrap<void>(baseAxios.post(`${SRV}/order/${orderId}/aftersale/${aftersaleId}/cancel`, {})),

  /**
   * GET /srv/store/list — pickup stores (order service, Wave 4). ASSUMED
   * contract (spec pending from the order worktree): tolerates either a
   * {list,total} wrapper or a bare array. The pickup checkout toggle only
   * appears when this succeeds with stores.
   */
  storeList: async (): Promise<IStore[]> => {
    const d = await unwrap<{ list?: IStore[] } | IStore[]>(baseAxios.get(`${SRV}/store/list`));
    return Array.isArray(d) ? d : d?.list ?? [];
  },

  // CJ dispute endpoints ("report a problem" on a dropship order). The order service
  // proxies CJ's dispute API with ~1s pacing between CJ calls, so these are SLOW
  // (2-6s typical) — each carries its own generous timeout like /actions/pay.
  disputeContext: (orderId: number | string) =>
    unwrap<IDisputeContext>(baseAxios.get(`${SRV}/order/${orderId}/disputes/context`, { timeout: 30000 })),
  disputeList: (orderId: number | string) =>
    unwrap<IDispute[]>(baseAxios.get(`${SRV}/order/${orderId}/disputes`, { timeout: 30000 })),
  disputeOpen: (
    orderId: number | string,
    body: {
      reasonId: number;
      reasonName?: string;
      expectType: 'REFUND' | 'REISSUE';
      message: string;
      imageUrls?: string[];
      lines: Array<{ lineItemId: string; quantity: number }>;
    }
  ) => unwrap<IDispute>(baseAxios.post(`${SRV}/order/${orderId}/disputes`, body, { timeout: 30000 })),
  disputeCancel: (orderId: number | string, disputeId: number) =>
    unwrap<void>(baseAxios.post(`${SRV}/order/${orderId}/disputes/${disputeId}/cancel`, {}, { timeout: 30000 })),
};
