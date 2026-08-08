// Admin order models, mirroring the litemall-db OrderVo / order-detail shapes
// returned by litemall-admin-api under /admin/order/* (list + detail). Prices
// arrive as numeric strings (BigDecimal) or numbers; read defensively in JSX.

// A row in the admin order list (litemall-db OrderVo).
export interface IOrderVo {
  id?: number;
  orderSn?: string;
  orderStatus?: number;
  actualPrice?: number | string;
  orderPrice?: number | string;
  freightPrice?: number | string;
  integralPrice?: number | string;
  addTime?: string;
  payTime?: string;
  userId?: number;
  userName?: string;
  userAvatar?: string;
  consignee?: string;
  mobile?: string;
  address?: string;
  shipChannel?: string;
  shipSn?: string;
  message?: string;
  // Fulfilment channel (Wave 4): 'express' (default) | 'pickup'. Read
  // defensively — older rows/DTOs may not carry it.
  deliveryType?: string;
}

// A line item on an order (litemall-db LitemallOrderGoods).
export interface IOrderGoods {
  id?: number;
  goodsId?: number;
  goodsName?: string;
  goodsSn?: string;
  picUrl?: string;
  specifications?: string[];
  price?: number | string;
  number?: number;
  comment?: number;
}

// The /admin/order/detail envelope: { order, orderGoods, user }.
export interface IOrderDetail {
  order?: IOrderVo & {
    couponPrice?: number | string;
    grouponPrice?: number | string;
    addressId?: number;
    orderStatusText?: string;
    handleOption?: Record<string, boolean>;
    // CJ dropship markers — present on the aggregate but NOT yet projected
    // onto the admin detail payload (order-side follow-up, see
    // docs/handoff-order-admin-cj.md). Read defensively.
    source?: string; // 'local' | 'cj'
    cjOrderId?: string;
    cjOrderNum?: string;
    trackNumber?: string;
    // Wave-23 manual-placement approval stamp (V59). May arrive as an ISO
    // string OR a LocalDateTime array — render through fromServerDateTime.
    cjPlacementApprovedTime?: string | number[];
    cjPlacementApprovedBy?: string;
    // In-store pickup fields (Wave 4, deliveryType === 'pickup') — all
    // optional; the store name may instead be encoded in `address` as
    // "PICKUP: <name>". Read defensively.
    storeId?: number;
    pickupName?: string;
    pickupMobile?: string;
    verifyCode?: string;
    verifyTime?: string;
    verifiedBy?: string;
  };
  orderGoods?: IOrderGoods[];
  user?: {
    id?: number;
    nickname?: string;
    mobile?: string;
    avatar?: string;
  };
}

// litemall order_status codes → label + Element tag colour. Mirrors the
// upstream litemall-admin order-status legend.
export const ORDER_STATUS: Record<number, { label: string; tag: 'info' | 'success' | 'warning' | 'danger' | 'primary' }> = {
  101: { label: 'Unpaid', tag: 'warning' },
  102: { label: 'Cancelled (user)', tag: 'info' },
  103: { label: 'Cancelled (system)', tag: 'info' },
  201: { label: 'Paid', tag: 'primary' },
  202: { label: 'Refund requested', tag: 'danger' },
  203: { label: 'Refunded', tag: 'danger' },
  301: { label: 'Shipped', tag: 'primary' },
  401: { label: 'Confirmed', tag: 'success' },
  402: { label: 'Completed', tag: 'success' },
};

export const orderStatusInfo = (code?: number): { label: string; tag: 'info' | 'success' | 'warning' | 'danger' | 'primary' } =>
  (code != null && ORDER_STATUS[code]) || { label: code != null ? `Status ${code}` : '—', tag: 'info' };

// Wave-23 manual CJ placement lifecycle, derived from the detail payload.
// Only 'awaiting-approval' offers the approve action (paid 201 only — a 202
// refund-applied order must never be approvable); 'placed' requires a real
// cjOrderId — an approval stamp alone is "approved, awaiting the sweep".
export type CjPlacementState = 'not-applicable' | 'not-paid' | 'awaiting-approval' | 'approved-awaiting-placement' | 'placed';

export const cjPlacementState = (o?: {
  source?: string;
  orderStatus?: number;
  cjOrderId?: string;
  cjPlacementApprovedTime?: string | number[];
}): CjPlacementState => {
  if (!o || o.source !== 'cj') return 'not-applicable';
  if (o.cjOrderId) return 'placed';
  if (o.cjPlacementApprovedTime != null && o.cjPlacementApprovedTime !== '') return 'approved-awaiting-placement';
  if (o.orderStatus === 201) return 'awaiting-approval';
  return 'not-paid';
};
