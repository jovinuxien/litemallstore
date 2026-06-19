import { IItemCart } from 'app/shared/model/cart/cart.models';

import { baseAxios, SRV, unwrap } from './http';

/**
 * Cart against the order-service REST contract (lb://order-service-app). The
 * live `LitemallCartController` is `@RequestMapping("/srv/cart")` and mounts its
 * verbs under `/items` (verified against the running order service):
 *   GET    /srv/cart/items            list the user's cart
 *   POST   /srv/cart/items            add a line
 *   PUT    /srv/cart/items/{cartId}   update a line
 *   DELETE /srv/cart/items/{cartId}   remove a line
 *   DELETE /srv/cart/items            clear
 *
 * The checkout summary endpoint (totals / freight / available coupons) is NOT
 * built yet — see CheckoutSummary below and docs/SRV-FOLLOWUPS.md. Callers must
 * tolerate its absence (the SPA falls back to a client-side total).
 */
export interface AddToCartBody {
  goodsId: number | string;
  productId: number;
  number: number;
}

export interface CheckoutSummary {
  addressId?: number;
  checkedAddress?: unknown;
  goodsTotalPrice?: number;
  freightPrice?: number;
  couponPrice?: number;
  orderTotalPrice?: number;
  actualPrice?: number;
  availableCouponLength?: number;
  checkedGoodsList?: IItemCart[];
}

export const cartApi = {
  list: () => unwrap<{ cartList: IItemCart[]; cartTotal: unknown }>(baseAxios.get(`${SRV}/cart/items`)),
  add: (body: AddToCartBody) => unwrap(baseAxios.post(`${SRV}/cart/items`, body)),
  update: (cartId: number, item: Partial<IItemCart>) => unwrap(baseAxios.put(`${SRV}/cart/items/${cartId}`, item)),
  remove: (cartId: number) => unwrap(baseAxios.delete(`${SRV}/cart/items/${cartId}`)),
  clear: () => unwrap(baseAxios.delete(`${SRV}/cart/items`)),
  // TODO(/srv follow-up: order) — checkout summary endpoint not implemented yet.
  checkout: (params: { addressId?: number; couponId?: number; cartId?: number }) =>
    unwrap<CheckoutSummary>(baseAxios.get(`${SRV}/cart/checkout`, { params })),
};
