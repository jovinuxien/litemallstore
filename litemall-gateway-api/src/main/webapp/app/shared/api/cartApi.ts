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

/** Unwrap the order service's DDD value objects: `{id: 5}` → 5, `{amount: 30.38}` → 30.38. */
const idOf = (v: unknown): number | undefined => {
  const raw = typeof v === 'object' && v !== null ? (v as { id?: unknown }).id : v;
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
};
const amountOf = (v: unknown): number | undefined => {
  const raw = typeof v === 'object' && v !== null ? (v as { amount?: unknown }).amount : v;
  const n = Number(raw);
  return Number.isFinite(n) ? n : undefined;
};

/**
 * A server cart line, as GET /srv/cart/items actually serves it: a BARE ARRAY of lines
 * whose identifier fields are nested value objects (`"cartId":{"id":13}`,
 * `"goodsId":{"id":10000095}`, `"price":{"amount":30.38}`) — unlike /srv/cart/checkout,
 * which serves the same lines flat. The slice's `{cartList}` expectation silently
 * matched neither, so the server cart read as empty on every fetch and a signed-in
 * customer could never get their basket back.
 */
interface ServerCartLine {
  cartId?: unknown;
  userId?: unknown;
  goodsId?: unknown;
  productId?: unknown;
  price?: unknown;
  goodsSn?: string;
  goodsName?: string;
  number?: number;
  specifications?: string[];
  checked?: boolean;
  picUrl?: string;
  source?: string;
  vid?: string;
}

const toCartItem = (line: ServerCartLine): IItemCart => {
  const goodsIdNum = idOf(line.goodsId);
  return {
    // Line id = the server cartId, so update/remove hit the real server line.
    id: idOf(line.cartId),
    userId: idOf(line.userId),
    goodsId: goodsIdNum != null ? String(goodsIdNum) : undefined,
    goodsSn: line.goodsSn,
    goodsName: line.goodsName,
    productId: idOf(line.productId),
    price: amountOf(line.price),
    number: line.number ?? 0,
    specifications: Array.isArray(line.specifications) ? line.specifications : [],
    checked: line.checked !== false,
    picUrl: line.picUrl,
    // Server lines carry no `source`; the goodsSn prefix is how a restored CJ line
    // keeps routing to the CJ checkout group (isCjItem checks source, Checkout.tsx).
    source: line.source ?? (line.goodsSn?.startsWith('cj_') ? 'cj' : undefined),
    vid: line.vid,
  };
};

export const cartApi = {
  list: async (): Promise<{ cartList: IItemCart[]; cartTotal: unknown }> => {
    const raw = await unwrap<unknown>(baseAxios.get(`${SRV}/cart/items`));
    const lines: ServerCartLine[] = Array.isArray(raw)
      ? raw
      : ((raw as { cartList?: ServerCartLine[] })?.cartList ?? []);
    const cartTotal = Array.isArray(raw) ? null : ((raw as { cartTotal?: unknown })?.cartTotal ?? null);
    return { cartList: lines.map(toCartItem), cartTotal };
  },
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
  // Wave-24.1: optional `cjLogisticName` — the customer's courier pick — so
  // the preview prices the upgrade delta exactly as submit will (pre-24.1
  // order service ignores it; unknown name ⇒ delta 0 server-side).
  checkout: (params: { addressId?: number; couponId?: number; countryCode?: string; cjLogisticName?: string }) =>
    unwrap<CheckoutSummary>(baseAxios.get(`${SRV}/cart/checkout`, { params })),
};
