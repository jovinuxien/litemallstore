import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
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
   * Wave 4 pickup checkout (litemall-order/docs/handoff-gateway-api-pickup.md:
   * storeId/pickupName/pickupMobile required for pickup, addressId ignored).
   * Local group only — CJ lines always ship.
   */
  deliveryType?: 'express' | 'pickup';
  storeId?: number;
  pickupName?: string;
  pickupMobile?: string;
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
  /** CARD path: the client-confirmed Stripe PaymentIntent id (stubbed for now). */
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
 * Numeric customer id from the JWT `sub` claim (the customer edge issues sub = userId).
 * The cart endpoints take userId in the body/param, unlike /order/submit which reads it
 * from the gateway-forwarded X-User-Id header.
 */
const customerUserId = (): number | null => {
  try {
    const token = sessionStorage.getItem('customerToken');
    if (!token) return null;
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    const id = Number(payload.sub);
    return Number.isFinite(id) ? id : null;
  } catch {
    return null;
  }
};

/**
 * Step 1 — create the order for ONE cart group. Mirrors the group's lines into the
 * server cart, then submits. A stock/validation failure creates no order. Checkout
 * dispatches this once per non-empty group, sequentially — the groups share the one
 * server cart and each mirror starts by wiping it.
 */
export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ group, items, paymentMethod, addressId, couponId, userCouponId, message, countryCode, deliveryType, storeId, pickupName, pickupMobile }, thunkApi) => {
    const userId = customerUserId();
    if (userId == null) {
      return thunkApi.rejectWithValue({ errno: 401, errmsg: 'Please sign in to place an order', data: null });
    }
    try {
      await baseAxios.delete(`${BASE_URL_CONTEXT}/cart/items`, { params: { userId } });
      let mirrored = 0;
      for (const it of items) {
        const goodsId = Number(it.goodsId);
        if (!Number.isFinite(goodsId)) continue;
        // eslint-disable-next-line no-await-in-loop
        await baseAxios.post(`${BASE_URL_CONTEXT}/cart/items`, {
          userId,
          goodsId,
          productId: it.productId,
          number: it.number,
          specifications: it.specifications,
          goodsSn: it.goodsSn,
          goodsName: it.goodsName,
          price: it.price,
          picUrl: it.picUrl,
        });
        mirrored += 1;
      }
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
 * Step 2 — pay a placed order. WALLET debits server-side (insufficient balance ->
 * 402, order left unpaid); CARD carries a Stripe PaymentIntent id (stubbed until real
 * Stripe Elements land). On success the order is PAID; for a source='cj' order the
 * pay also placed it at CJ (a CJ rejection rolled the payment back and rejects here).
 */
export const payOrder = createAsyncThunk<PlacedOrder, PayOrderParams, { rejectValue: ApiResult<null> }>(
  'order/pay',
  async ({ orderId, paymentMethod, paymentIntentId, group }, thunkApi) => {
    try {
      const payload: { paymentMethod: string; paymentIntentId?: string } = {
        paymentMethod: toBackendPaymentMethod(paymentMethod),
      };
      if (paymentMethod === 'CARD') {
        // STUB: no Stripe Elements/keys wired yet, so hand the order service a placeholder
        // confirmed PaymentIntent id. Replace with a real client-confirmed intent later.
        payload.paymentIntentId = paymentIntentId ?? `pi_stub_${orderId}`;
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
