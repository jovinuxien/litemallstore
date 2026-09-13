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
  /**
   * The order is REFUND_REQUEST (202) and the customer may take the request back
   * (lifecycle D4). The customer payload has no numeric status — this flag IS the 202 key.
   */
  withdrawRefund?: boolean;
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
  /** ISO-3166 alpha-2 destination country (V27/V48); absent on legacy/pickup orders. */
  countryCode?: string;
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
   * Customer-facing fulfilment phrase for CJ orders, server-rendered (Wave 8; two more
   * phrases since the order lifecycle packages — "Processing — being reviewed by our
   * team", "Could not be fulfilled — our support team will contact you"). A SERVER
   * string: shown verbatim, never mapped to a client list. Null/absent on local orders.
   */
  fulfillmentStatus?: string | null;
  /**
   * Wave 4 pickup fields (litemall-order/docs/handoff-gateway-api-pickup.md).
   * NON_NULL: express orders carry none of these. verifyCode appears only once
   * paid (owner-scoped read); verifyTime set = already collected. Store info is
   * NOT embedded — fetch it via `GET /srv/store/detail?id={storeId}`.
   */
  deliveryType?: string; // 'express' | 'pickup'
  storeId?: number;
  verifyCode?: string;
  verifyTime?: string | number[]; // LocalDateTime tuple on local rows
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
  cj?: {
    logisticName?: string;
    logisticAging?: string;
    /** Wave-28: the warehouse country this shipment was actually QUOTED from (ISO-2).
     *  A fact about the quote, not a restatement of the stock measurement — the order
     *  side falls back to CN when an EU quote comes back empty. Absent pre-Wave-28. */
    originCountry?: string | null;
    /** V52: every line CJ offers for this shipment — the delivery-option chooser's data.
     *  Wave-24.1: `upgradeDelta` (post-fx decimal vs the default line; 0.00 ⇒ "Included",
     *  null ⇒ unpriceable) feeds the labels VERBATIM — raw CJ courier costs are never
     *  surfaced. Absent (pre-24.1 order half) ⇒ the chooser renders label-less. */
    options?: { logisticName?: string; logisticAging?: string; upgradeDelta?: number | null; originCountry?: string | null }[];
  } | null;
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
 * One pickup store from `GET /srv/store/{list,detail}` (order service, Wave 4;
 * contract confirmed against litemall-order/docs/handoff-gateway-api-pickup.md).
 * Only visible (is_show) stores are served; hidden/unknown detail → errno 404.
 */
export interface IStore {
  id?: number;
  name?: string;
  intro?: string;
  address?: string;
  detailedAddress?: string;
  logo?: string;
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

/**
 * One step of `GET /srv/order/{id}/timeline` (oldest first). Codes + server labels for the
 * from/to statuses; `changeType` is the stable vocabulary (see modules/order/timelineCopy);
 * `changeMessage` is the server's account of the step; `operator` is `user`, `system` or
 * `admin:<id>`. changeTime: ISO string or LocalDateTime tuple.
 */
export interface IOrderTimelineEntry {
  fromStatus?: number | null;
  fromStatusText?: string | null;
  toStatus?: number | null;
  toStatusText?: string | null;
  changeType?: string | null;
  changeMessage?: string | null;
  operator?: string | null;
  changeTime?: string | number[] | null;
}
