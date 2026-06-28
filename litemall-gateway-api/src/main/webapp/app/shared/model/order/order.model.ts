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
}
