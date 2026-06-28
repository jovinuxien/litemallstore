import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';

/**
 * Customer order placement + payment. Both calls go through the gateway to the
 * order service as an authenticated customer — baseAxios relays the customer JWT
 * as Bearer and the machine-token relay forwards it downstream; the buyer is
 * bound server-side from the gateway-injected `X-User-Id` header (never sent by
 * the SPA).
 *
 * CONTRACT (verified against the running order service's LitemallOrderRestController):
 * `submit` and `actions/pay` BOTH return a bare `OrderOperationDtoResponse`
 *   { success, status, message, operationType, orderId, orderSn, actualPrice,
 *     paymentRequired, errorCode, ... }
 * with the outcome carried by the HTTP status, NOT an {errno,errmsg,data} envelope:
 *   - submit: 201 Created on success; 422 Unprocessable Entity on a stock/validation
 *     failure (no order created).
 *   - pay:    200 OK on success; 402 Payment Required on insufficient wallet balance /
 *     payment failure (order left unpaid). axios rejects on the 4xx, so the failure
 *     body arrives on `error.response.data`.
 *
 * Placement is a TWO-STEP flow: `POST /srv/order/submit` (LitemallPlaceOrderCommand)
 * creates the order, then `POST /srv/order/{id}/actions/pay` (PaymentActionRequest)
 * charges it. `paymentMethod` is therefore NOT a submit field — it belongs to the
 * separate pay step.
 */
export type CheckoutPaymentMethod = 'CARD' | 'WALLET';

/** Map the SPA payment choice to the order service's PaymentMethod enum name. */
const toBackendPaymentMethod = (m: CheckoutPaymentMethod): string => (m === 'WALLET' ? 'WALLET' : 'CREDIT_CARD');

/** Pull a human-readable message out of an OrderOperationDtoResponse error body. */
const messageFromError = (error: unknown, fallback: string): string => {
  const data = (error as { response?: { data?: { message?: string; errmsg?: string } } })?.response?.data;
  return data?.message ?? data?.errmsg ?? (error as { message?: string })?.message ?? fallback;
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
}

/**
 * The `LitemallPlaceOrderCommand` fields the SPA controls. `cartId` 0 means
 * "checkout the whole checked cart"; `addressId` is a saved-address id. The
 * buyer (`userId`) is bound server-side from the gateway `X-User-Id` header.
 */
export interface PlaceOrderParams {
  cartId?: number;
  addressId: number;
  couponId?: number;
  userCouponId?: number;
  message?: string;
}

/** A successfully placed (but not yet necessarily paid) order. */
export interface PlacedOrder {
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
 * Step 1 — create the order. On 201 the order exists (stock reserved, NOT yet
 * paid); a 422 means a stock/validation failure created NO order and is surfaced
 * as a rejection.
 */
export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ cartId, addressId, couponId, userCouponId, message }, thunkApi) => {
    try {
      // LitemallPlaceOrderCommand shape — no userId (gateway injects X-User-Id),
      // no inline items/shipping, no paymentMethod (that is the pay step). Groupon
      // ids default to 0 (not a groupon order).
      const payload = {
        cartId: cartId ?? 0,
        addressId,
        couponId: couponId ?? 0,
        userCouponId: userCouponId ?? 0,
        message: message ?? '',
        grouponRulesId: 0,
        grouponLinkId: 0,
      };
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, payload);
      const body = response.data as OrderOperationResponse;
      if (body.success === false || body.orderId == null) {
        return thunkApi.rejectWithValue({ errno: 1, errmsg: body.message ?? 'Order placement failed', data: null });
      }
      return { orderId: body.orderId, orderSn: body.orderSn, actualPrice: body.actualPrice, paid: false };
    } catch (error) {
      // 422 etc. — stock/validation failure; no order created.
      return thunkApi.rejectWithValue({ errno: 422, errmsg: messageFromError(error, 'Order placement failed'), data: null });
    }
  }
);

/**
 * Step 2 — pay a placed order. WALLET debits the customer's wallet server-side
 * (insufficient balance → 402, order left unpaid); CARD carries a Stripe
 * PaymentIntent id (stubbed until real Stripe Elements land). On success the
 * order is PAID.
 */
export const payOrder = createAsyncThunk<PlacedOrder, PayOrderParams, { rejectValue: ApiResult<null> }>(
  'order/pay',
  async ({ orderId, paymentMethod, paymentIntentId }, thunkApi) => {
    try {
      const payload: { paymentMethod: string; paymentIntentId?: string } = {
        paymentMethod: toBackendPaymentMethod(paymentMethod),
      };
      if (paymentMethod === 'CARD') {
        // STUB: no Stripe Elements/keys wired in this SPA yet, so we hand the order
        // service a placeholder confirmed PaymentIntent id. Replace with a real
        // client-confirmed intent when Stripe is integrated (docs/SRV-FOLLOWUPS.md).
        payload.paymentIntentId = paymentIntentId ?? `pi_stub_${orderId}`;
      }
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/${orderId}/actions/pay`, payload);
      const body = response.data as OrderOperationResponse;
      if (body.success === false) {
        return thunkApi.rejectWithValue({ errno: 1, errmsg: body.message ?? 'Payment failed', data: null });
      }
      return { orderId, orderSn: body.orderSn, actualPrice: body.actualPrice, paid: true, paymentMethod };
    } catch (error) {
      // 402 Payment Required — e.g. insufficient wallet balance; order left unpaid.
      return thunkApi.rejectWithValue({ errno: 402, errmsg: messageFromError(error, 'Payment failed'), data: null });
    }
  }
);

interface OrderState extends BaseState<{ lastOrder: PlacedOrder | null }> {
  /** Which step is in flight, so the UI can label "Placing…" vs "Paying…". */
  phase: 'idle' | 'placing' | 'paying';
}

const initialState: OrderState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { lastOrder: null },
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
      state.data.lastOrder = null;
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
        state.data.lastOrder = action.payload;
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
        // Merge the paid flag/method onto the already-placed order.
        state.data.lastOrder = { ...(state.data.lastOrder ?? {}), ...action.payload };
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
