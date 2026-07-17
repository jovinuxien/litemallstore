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

/**
 * Server-authoritative checkout totals (order Wave-7, handoff §4).
 *
 * <p>The server computes this with the SAME freight service, coupon facade and tax port
 * that `submit` uses, so the preview and the charge agree by construction. The client
 * must not recompute any of it — with tax it provably cannot, and without tax it merely
 * disagreed silently.
 */
export interface CheckoutSummary {
  addressId?: number;
  checkedAddress?: unknown;
  goodsTotalPrice?: number;
  freightPrice?: number;
  /** Wave-7. 0.00 unless tax is enabled AND a countryCode was supplied. */
  taxPrice?: number;
  couponPrice?: number;
  orderTotalPrice?: number;
  /** The number to charge. */
  actualPrice?: number;
  checkedGoodsList?: IItemCart[];
}

export const cartApi = {
  list: () => unwrap<{ cartList: IItemCart[]; cartTotal: unknown }>(baseAxios.get(`${SRV}/cart/items`)),
  add: (body: AddToCartBody) => unwrap(baseAxios.post(`${SRV}/cart/items`, body)),
  update: (cartId: number, item: Partial<IItemCart>) => unwrap(baseAxios.put(`${SRV}/cart/items/${cartId}`, item)),
  remove: (cartId: number) => unwrap(baseAxios.delete(`${SRV}/cart/items/${cartId}`)),
  clear: () => unwrap(baseAxios.delete(`${SRV}/cart/items`)),
  /**
   * Server-computed totals. All params optional — the cart previews before an address is
   * picked. `countryCode` is what makes tax resolvable: without it `taxPrice` is 0.00 and
   * the total is NOT final, so send the same country used for checkout.
   *
   * Throws on 503 (tax enabled + provider unreachable). That is deliberate and must NOT
   * be swallowed into a client-side sum: the total we would guess is one the server will
   * refuse to charge. Render it as retryable.
   */
  checkout: (params: { addressId?: number; couponId?: number; countryCode?: string }) =>
    unwrap<CheckoutSummary>(baseAxios.get(`${SRV}/cart/checkout`, { params })),
};
