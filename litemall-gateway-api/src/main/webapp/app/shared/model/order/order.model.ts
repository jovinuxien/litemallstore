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
}

/**
 * Checkout freight/logistics quote (POST /srv/order/freight-quote). freightPrice is what
 * submit will actually charge; `cj` is an informational carrier + delivery estimate.
 */
export interface IFreightQuote {
  freightPrice?: number;
  freeShippingThreshold?: number;
  cj?: { logisticName?: string; logisticAging?: string } | null;
  cjNote?: string | null;
}
