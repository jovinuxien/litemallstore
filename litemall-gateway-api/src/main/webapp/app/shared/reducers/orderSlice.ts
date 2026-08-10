import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { cartApi, CheckoutSummary } from 'app/shared/api/cartApi';
import { IItemCart } from 'app/shared/model/cart/cart.models';

/**
 * Customer order placement + payment against the cart-based order service. baseAxios
 * relays the customer JWT as Bearer; the gateway forwards X-User-Id downstream.
 *
 * Local and CJ Dropshipping lines run the SAME two-step place->pay flow, one order
 * per cart group (the order service rejects a cart mixing CJ and local goods):
 * mirror the group into the server cart, then `POST /srv/order/submit { cartId:0,
 * addressId, ... }` creates an UNPAID order — for an all-CJ group the order carries
 * `source='cj'` and the submit body's `countryCode` (CJ needs a destination
 * country). Both submit and `POST /srv/order/{id}/actions/pay` return a bare
 * `OrderOperationDtoResponse` ({success,orderId,orderSn,actualPrice,message}) with
 * the outcome on the HTTP status (201 created / 422 stock / 200 paid / 402
 * insufficient balance) — NOT an {errno,errmsg,data} envelope. Paying a CJ order
 * also places it at CJ inside the payment transaction (3-10s typical; a CJ
 * rejection rolls the payment back and the order stays unpaid).
 */
export type CheckoutPaymentMethod = 'CARD' | 'WALLET';

/** Which cart group an order was submitted for — local goods or CJ dropship goods. */
export type OrderGroup = 'local' | 'cj';

/** Map the SPA payment choice to the order service's PaymentMethod enum name. */
const toBackendPaymentMethod = (m: CheckoutPaymentMethod): string => (m === 'WALLET' ? 'WALLET' : 'CREDIT_CARD');

/** Pull a human-readable message out of an OrderOperationDtoResponse error body. */
const messageFromError = (error: unknown, fallback: string): string => {
  const data = (error as { response?: { data?: { message?: string; errmsg?: string; errorCode?: string } } })?.response?.data;
  return data?.message ?? data?.errmsg ?? data?.errorCode ?? (error as { message?: string })?.message ?? fallback;
};

export interface ShippingInfo {
  name: string;
  mobile: string;
  email: string;
  address: string;
  addressTwo?: string;
  region: string;
  kommune: string;
  zip: string;
  // Required only when the cart contains CJ lines (CJ placement needs a real country).
  country?: string;
  countryCode?: string;
}

export interface PlaceOrderParams {
  /** Which cart group this submit covers — the items must all belong to it. */
  group: OrderGroup;
  items: IItemCart[];
  paymentMethod: CheckoutPaymentMethod;
  /** Saved-address id for the order (omitted -> order service uses the default). */
  addressId?: number;
  couponId?: number;
  userCouponId?: number;
  message?: string;
  /**
   * ISO destination country — required for the CJ group (persisted on the order and
   * used for CJ placement at pay time); optional and harmless on local.
   */
  countryCode?: string;
  /**
   * V52 delivery-option chooser: the CJ logistics line the customer picked from the
   * freight-quote options. Optional — placement falls back to default/cheapest when
   * absent or no longer offered.
   */
  cjLogisticName?: string;
  /**
   * Wave 4 pickup checkout (litemall-order/docs/handoff-gateway-api-pickup.md:
   * storeId/pickupName/pickupMobile required for pickup, addressId ignored).
   * Local group only — CJ lines always ship.
   */
  deliveryType?: 'express' | 'pickup';
  storeId?: number;
  pickupName?: string;
  pickupMobile?: string;
  /**
   * Wave-21 group-buy: the buyer's OWN combination slot id
   * (spec-groupon-priced-submit-contract.md). When present the order service
   * validates the slot (owner, live status, no order attached) and prices the
   * campaign line at the GROUP price — a stale/expired slot is rejected with a
   * typed message, never silently re-priced. Omitted ⇒ byte-identical submit.
   */
  pinkId?: number;
}

export interface PlacedOrder {
  group: OrderGroup;
  orderId: number;
  orderSn?: string;
  actualPrice?: number;
  paid?: boolean;
  paymentMethod?: CheckoutPaymentMethod;
}

export interface PayOrderParams {
  orderId: number;
  paymentMethod: CheckoutPaymentMethod;
  /**
   * CARD path: the REAL client-confirmed Stripe PaymentIntent id. Required for CARD —
   * optional only because WALLET carries none. The server verifies it against Stripe.
   */
  paymentIntentId?: string;
  /**
   * Which lastOrders slot this order belongs to; defaults to 'local' (the
   * standalone /pay screen doesn't know the order's source).
   */
  group?: OrderGroup;
}

/** Orders created by the current checkout, keyed by cart group. */
export interface LastOrders {
  local?: PlacedOrder;
  cj?: PlacedOrder;
}

/** Shape of the OrderOperationDtoResponse fields the SPA reads. */
interface OrderOperationResponse {
  success?: boolean;
  orderId?: number;
  orderSn?: string;
  actualPrice?: number;
  message?: string;
}

/**
 * /actions/pay places the CJ order inside the payment transaction for source='cj'
 * orders (3-10s typical, occasionally slower). Override the 5s global axios timeout
 * so a slow CJ placement isn't aborted client-side mid-transaction. Applied to
 * every pay — a local pay finishing fast is unaffected.
 */
const PAY_TIMEOUT_MS = 45000;

/**
 * Wave-7: every /srv/cart endpoint now takes the buyer from the gateway-forwarded
 * X-User-Id header, like /order/submit always has — so the SPA no longer decodes the JWT
 * to assert who it is. (It never should have: a userId the client supplies is a request
 * to be believed, not proof. Sending it was the cart IDOR.)
 */
const isSignedIn = (): boolean => !!sessionStorage.getItem('customerToken');

/**
 * Replace the server cart with ONE group's lines. Returns how many lines were mirrored.
 *
 * <p>Shared by submit and the checkout preview deliberately: `GET /srv/cart/checkout`
 * prices the SERVER cart, so a preview that didn't mirror first would price whatever was
 * left over from last time — or, for a fresh session, nothing at all. Routing both through
 * this is what makes "preview == charge" true by construction rather than by hope.
 *
 * <p>Lines whose goodsId isn't numeric (stale `cj_<pid>` keys from an old session cart)
 * are skipped, not sent.
 */
const mirrorGroupToServerCart = async (items: IItemCart[]): Promise<number> => {
  await baseAxios.delete(`${BASE_URL_CONTEXT}/cart/items`);
  let mirrored = 0;
  for (const it of items) {
    const goodsId = Number(it.goodsId);
    if (!Number.isFinite(goodsId)) continue;
    // Identifiers + quantity ONLY (order Wave-7, handoff §1/§2). price, goodsSn,
    // goodsName, picUrl, specifications and userId are gone from AddCartItemRequest: the
    // line is re-resolved from the catalog server-side, and identity comes from the
    // gateway's X-User-Id. The server ignores extra fields rather than rejecting them, so
    // this could have been left alone — but a `price` the server discards still reads as
    // authoritative, and those display fields used to be copied verbatim into permanent
    // order_goods rows.
    // eslint-disable-next-line no-await-in-loop
    await baseAxios.post(`${BASE_URL_CONTEXT}/cart/items`, {
      goodsId,
      productId: it.productId,
      number: it.number,
    });
    mirrored += 1;
  }
  return mirrored;
};

/** Server-computed money for the whole checkout. Every field comes from the server. */
export interface CheckoutTotals {
  goodsTotalPrice: number;
  freightPrice: number;
  taxPrice: number;
  couponPrice: number;
  actualPrice: number;
}

/** A 503 from `/srv/cart/checkout`: tax is enabled and its provider is unreachable. */
export class TaxUnavailableError extends Error {}

/**
 * Server-authoritative totals for the checkout (order Wave-7, handoff §4).
 *
 * <p>Mirrors each group then prices it, summing across groups — the same sequence, in the
 * same order, that submit will run, so the previewed total is the total charged. Groups
 * exist because the order service rejects a cart mixing CJ and local goods: each becomes
 * its own order with its own freight, and the customer sees one combined number.
 *
 * <p>The coupon rides the FIRST group only, matching submit (`handlePlaceOrder` sends it
 * with the first submitted order). Previewing it against every group would over-state the
 * discount.
 *
 * <p>Throws {@link TaxUnavailableError} on 503. Callers MUST NOT fall back to a
 * client-side sum: tax fails closed by design, and a total we invent is one the server has
 * already said it will refuse to charge.
 */
export const previewCheckoutTotals = async (
  groups: IItemCart[][],
  params: { addressId?: number; couponId?: number; countryCode?: string; cjLogisticName?: string },
): Promise<CheckoutTotals> => {
  const totals: CheckoutTotals = {
    goodsTotalPrice: 0,
    freightPrice: 0,
    taxPrice: 0,
    couponPrice: 0,
    actualPrice: 0,
  };
  let couponApplied = false;
  for (const items of groups) {
    if (items.length === 0) continue;
    // eslint-disable-next-line no-await-in-loop
    const mirrored = await mirrorGroupToServerCart(items);
    if (mirrored === 0) continue;
    let summary: CheckoutSummary;
    try {
      // eslint-disable-next-line no-await-in-loop
      summary = await cartApi.checkout({
        addressId: params.addressId,
        couponId: couponApplied ? undefined : params.couponId,
        countryCode: params.countryCode,
        // Harmless on the local group — freight resolution only reads it for
        // CJ carts (Wave-24.1 upgrade-delta contract).
        cjLogisticName: params.cjLogisticName,
      });
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
        ?? (error as { errno?: number })?.errno;
      if (status === 503) {
        throw new TaxUnavailableError(
          messageFromError(error, "We can't total your cart right now — please try again."),
        );
      }
      throw error;
    }
    totals.goodsTotalPrice += summary.goodsTotalPrice ?? 0;
    totals.freightPrice += summary.freightPrice ?? 0;
    totals.taxPrice += summary.taxPrice ?? 0;
    totals.couponPrice += summary.couponPrice ?? 0;
    totals.actualPrice += summary.actualPrice ?? 0;
    couponApplied = true;
  }
  return totals;
};

/**
 * Step 1 — create the order for ONE cart group. Mirrors the group's lines into the
 * server cart, then submits. A stock/validation failure creates no order. Checkout
 * dispatches this once per non-empty group, sequentially — the groups share the one
 * server cart and each mirror starts by wiping it.
 */
export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ group, items, paymentMethod, addressId, couponId, userCouponId, message, countryCode, cjLogisticName, deliveryType, storeId, pickupName, pickupMobile, pinkId }, thunkApi) => {
    if (!isSignedIn()) {
      return thunkApi.rejectWithValue({ errno: 401, errmsg: 'Please sign in to place an order', data: null });
    }
    try {
      const mirrored = await mirrorGroupToServerCart(items);
      if (mirrored === 0) {
        // e.g. stale cj_<pid> lines from an old session cart — never submit an empty cart.
        return thunkApi.rejectWithValue({
          errno: 400,
          errmsg: 'These items can no longer be ordered — remove them and re-add from the product page.',
          data: null,
        });
      }
      // LitemallPlaceOrderCommand — buyer bound from the gateway X-User-Id header.
      const submitBody: Record<string, unknown> = {
        cartId: 0,
        couponId: couponId ?? 0,
        userCouponId: userCouponId ?? 0,
        message: message ?? '',
        grouponRulesId: 0,
        grouponLinkId: 0,
      };
      if (addressId != null) submitBody.addressId = addressId; // else order service uses the default
      if (countryCode) submitBody.countryCode = countryCode; // CJ placement needs it at pay time
      if (cjLogisticName) submitBody.cjLogisticName = cjLogisticName; // V52 carrier pick, optional
      if (pinkId != null) submitBody.pinkId = pinkId; // Wave-21 group-buy slot — group price applied server-side
      // Pickup (Wave 4, assumed contract — only sent when the customer chose
      // pickup, so express submits are byte-identical to today's).
      if (deliveryType === 'pickup') {
        submitBody.deliveryType = 'pickup';
        if (storeId != null) submitBody.storeId = storeId;
        if (pickupName) submitBody.pickupName = pickupName;
        if (pickupMobile) submitBody.pickupMobile = pickupMobile;
      }
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, submitBody);
      const data = response.data as OrderOperationResponse;
      if (data.success === false || data.orderId == null) {
        return thunkApi.rejectWithValue({ errno: 1, errmsg: data.message ?? 'Order placement failed', data: null });
      }
      return { group, orderId: data.orderId, orderSn: data.orderSn, actualPrice: data.actualPrice, paid: false, paymentMethod };
    } catch (error) {
      return thunkApi.rejectWithValue({
        errno: (error as { response?: { status?: number } }).response?.status ?? 500,
        errmsg: messageFromError(error, 'Order placement failed'),
        data: null,
      });
    }
  }
);

/**
 * Step 2 — pay a placed order. WALLET debits server-side (insufficient balance -> 402,
 * order left unpaid); CARD carries a real PaymentIntent id that Checkout has already
 * confirmed with Stripe Elements. On success the order is PAID; for a source='cj' order
 * the pay also placed it at CJ (a CJ rejection rolled the payment back and rejects here).
 *
 * Wave-7: the client call is a latency optimisation, NOT the authority. Stripe's webhook
 * (POST /srv/order/webhook/stripe/order-paid) is what ultimately marks the order paid, so
 * a customer who closes the tab mid-confirm still gets a paid order.
 */
export const payOrder = createAsyncThunk<PlacedOrder, PayOrderParams, { rejectValue: ApiResult<null> }>(
  'order/pay',
  async ({ orderId, paymentMethod, paymentIntentId, group }, thunkApi) => {
    try {
      const payload: { paymentMethod: string; paymentIntentId?: string } = {
        paymentMethod: toBackendPaymentMethod(paymentMethod),
      };
      if (paymentMethod === 'CARD') {
        // A real, client-confirmed PaymentIntent id — Checkout confirms with Stripe
        // Elements before dispatching. There is deliberately no fallback: the server now
        // retrieves the intent and asserts status/amount/currency/metadata.orderId, so a
        // fabricated id is rejected. Reaching here without one is a bug, not a state to
        // paper over with a placeholder.
        if (!paymentIntentId) {
          return thunkApi.rejectWithValue({
            errno: 400,
            errmsg: 'Card payment was not completed — please try again.',
            data: null,
          });
        }
        payload.paymentIntentId = paymentIntentId;
      }
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/${orderId}/actions/pay`, payload, { timeout: PAY_TIMEOUT_MS });
      const body = response.data as OrderOperationResponse;
      if (body.success === false) {
        return thunkApi.rejectWithValue({ errno: 1, errmsg: body.message ?? 'Payment failed', data: null });
      }
      return { group: group ?? 'local', orderId, orderSn: body.orderSn, actualPrice: body.actualPrice, paid: true, paymentMethod };
    } catch (error) {
      // 402 Payment Required (insufficient wallet) / pay-failed envelope (e.g. "CJ
      // fulfillment could not be placed: ...") — order left unpaid either way.
      const status = (error as { response?: { status?: number } }).response?.status;
      const timedOut = (error as { code?: string }).code === 'ECONNABORTED';
      return thunkApi.rejectWithValue({
        errno: status ?? (timedOut ? 504 : 500),
        errmsg: timedOut
          ? 'Payment is taking longer than expected. Check My Orders before retrying — the order may already be paid.'
          : messageFromError(error, 'Payment failed'),
        data: null,
      });
    }
  }
);

interface OrderState extends BaseState<{ lastOrders: LastOrders }> {
  /** Which step is in flight, so the UI can label "Placing…" vs "Paying…". */
  phase: 'idle' | 'placing' | 'paying';
}

const initialState: OrderState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { lastOrders: {} },
  phase: 'idle',
};

const orderSlice = createSlice({
  name: 'order',
  initialState,
  reducers: {
    resetOrderState: state => {
      state.loading = 'idle';
      state.errorMessage = null;
      state.errorNumber = null;
      state.phase = 'idle';
      state.data.lastOrders = {};
    },
  },
  extraReducers: builder => {
    builder
      .addCase(placeOrder.pending, state => {
        state.loading = 'pending';
        state.phase = 'placing';
        state.errorMessage = null;
      })
      .addCase(placeOrder.fulfilled, (state, action) => {
        state.loading = 'idle';
        state.phase = 'idle';
        state.data.lastOrders[action.payload.group] = action.payload;
      })
      .addCase(placeOrder.rejected, (state, action) => {
        state.loading = 'failed';
        state.phase = 'idle';
        state.errorMessage = action.payload?.errmsg ?? 'Order placement failed';
        state.errorNumber = action.payload?.errno ?? 500;
      })
      .addCase(payOrder.pending, state => {
        state.loading = 'pending';
        state.phase = 'paying';
        state.errorMessage = null;
      })
      .addCase(payOrder.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.phase = 'idle';
        const group = action.payload.group;
        state.data.lastOrders[group] = { ...(state.data.lastOrders[group] ?? {}), ...action.payload };
      })
      .addCase(payOrder.rejected, (state, action) => {
        state.loading = 'failed';
        state.phase = 'idle';
        state.errorMessage = action.payload?.errmsg ?? 'Payment failed';
        state.errorNumber = action.payload?.errno ?? 500;
      });
  },
});

export const { resetOrderState } = orderSlice.actions;
export default orderSlice.reducer;
