/**
 * Customer order view models, shaped after the litemall-vue order list/detail
 * payloads (the agreed `/srv/order` contract the order worktree owns). Prices
 * may arrive as a plain number or `LitemallMoney {amount}` — read with
 * `priceNum()` in the views, as elsewhere.
 */
export interface IOrderGoods {
  id?: number;
  goodsId?: number | string;
  goodsName?: string;
  picUrl?: string;
  number?: number;
  price?: number | { amount: number };
  specifications?: string[];
}

export interface IOrderHandleOption {
  cancel?: boolean;
  delete?: boolean;
  pay?: boolean;
  confirm?: boolean;
  refund?: boolean;
  comment?: boolean;
  rebuy?: boolean;
  /** Aftersale application window open (Wave-2 vertical). */
  aftersale?: boolean;
}

export interface IOrderListItem {
  id?: number;
  orderSn?: string;
  actualPrice?: number | { amount: number };
  orderStatusText?: string;
  handleOption?: IOrderHandleOption;
  aftersaleStatus?: number;
  goodsList?: IOrderGoods[];
  source?: string; // 'local' | 'cj' — 'cj' rows are dropship orders
  cjOrderNum?: string;
  /** Wave 4 pickup (assumed contract, order spec pending): 'express' | 'pickup'. */
  deliveryType?: string;
}

export interface IOrderDetail {
  id?: number;
  orderSn?: string;
  addTime?: string;
  consignee?: string;
  mobile?: string;
  address?: string;
  orderStatusText?: string;
  handleOption?: IOrderHandleOption;
  goodsPrice?: number | { amount: number };
  freightPrice?: number | { amount: number };
  couponPrice?: number | { amount: number };
  actualPrice?: number | { amount: number };
  orderGoods?: IOrderGoods[];
  source?: string; // 'local' | 'cj' — 'cj' rows are dropship orders
  cjOrderId?: string;
  cjOrderNum?: string;
  /** Logistics line the order ships with (the CJ line chosen at placement). */
  shipChannel?: string;
  /**
   * Wave 4 pickup fields (ASSUMED contract — order worktree spec pending; the
   * card renders only when present, so absence is harmless).
   */
  deliveryType?: string; // 'express' | 'pickup'
  verifyCode?: string;
  pickupStore?: IStore | null;
  pickupName?: string;
  pickupMobile?: string;
}

/**
 * Checkout freight/logistics quote (POST /srv/order/freight-quote). freightPrice is what
 * submit will actually charge; `cj` is an informational carrier + delivery estimate.
 */
export interface IFreightQuote {
  freightPrice?: number;
  freeShippingThreshold?: number;
  /** Wave 4: TEMPLATE | SYSTEM_FLAT | FREE_MIN — how freightPrice was priced. */
  source?: string;
  /**
   * Wave 4: one entry per priced group. Detail-only — freightPrice is the
   * COMBINED charged figure (combine-mode max, not the breakdown sum).
   */
  breakdown?: IFreightBreakdownEntry[];
  cj?: { logisticName?: string; logisticAging?: string } | null;
  cjNote?: string | null;
}

/** One priced group in the Wave-4 freight-quote breakdown. */
export interface IFreightBreakdownEntry {
  templateId?: number | null;
  templateName?: string | null;
  source?: string;
  amount?: number;
  note?: string | null;
}

/**
 * Customer tracking payload from `GET /srv/order/{id}/tracking`
 * (litemall-order/docs/handoff-gateway-admin-cj-tracking.md — everything except
 * `shipped` and `events` may be null; not-shipped is shipped:false, never an error).
 */
export interface ITracking {
  shipped?: boolean;
  status?: string | null;
  carrier?: string | null;
  trackNumber?: string | null;
  origin?: string | null;
  destination?: string | null;
  deliveryDay?: string | null;
  lastMileCarrier?: string | null;
  lastTrackNumber?: string | null;
  events?: Array<{ time?: string; status?: string | null; description?: string | null }>;
}

/**
 * One pickup store from `GET /srv/store/list` (order service, Wave 4).
 * ASSUMED contract — the order worktree's store/pickup handoff spec hasn't
 * landed yet; the pickup UI is gated on this endpoint answering, so a field
 * rename is a low-risk fixup. Dependency: litemall-order Wave-4 stores task.
 */
export interface IStore {
  id?: number;
  name?: string;
  address?: string;
  phone?: string;
  businessHours?: string;
  latitude?: number;
  longitude?: number;
}

/**
 * One aftersale/RMA application (`/srv/order/{orderId}/aftersale`, Wave-2
 * vertical — litemall-order/docs/aftersale-vertical.md). type: 0 not-received
 * refund, 1 received/refund-only, 2 return-and-refund. status 1–5 lifecycle;
 * statusText is server-rendered.
 */
export interface IAftersale {
  id?: number;
  aftersaleSn?: string;
  orderId?: number;
  type?: number;
  reason?: string;
  amount?: number;
  pictures?: string[];
  comment?: string;
  status?: number;
  statusText?: string;
  handleTime?: string;
  addTime?: string;
}

/** One CJ dispute as `/srv/order/{id}/disputes` returns it. */
export interface IDispute {
  id?: number;
  businessDisputeId?: string;
  cjDisputeId?: string;
  reasonName?: string;
  expectType?: 'REFUND' | 'REISSUE';
  message?: string;
  status?: string;
  resolution?: 'REFUND' | 'REISSUE' | 'REJECTED';
  refundAmountUsd?: number;
  resendOrderCode?: string;
  cancelled?: boolean;
  open?: boolean;
  addTime?: string;
}

/** "Report a problem" form data from `/srv/order/{id}/disputes/context`. */
export interface IDisputeContext {
  lines: Array<{
    lineItemId: string;
    productName?: string;
    imageUrl?: string;
    unitPriceUsd?: number;
    maxQuantity: number;
  }>;
  reasons: Array<{ id: number; name: string }>;
  maxAmountUsd?: number;
  refundAllowed: boolean;
  reissueAllowed: boolean;
}
