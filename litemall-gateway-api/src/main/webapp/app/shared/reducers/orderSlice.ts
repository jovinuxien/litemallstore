import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IItemCart } from 'app/shared/model/cart/cart.models';

/**
 * Customer order placement + payment against the cart-based order service. baseAxios
 * relays the customer JWT as Bearer; the gateway forwards X-User-Id downstream.
 *
 * The cart is split by source:
 *  - LOCAL lines mirror the client cart into the server cart, then
 *    `POST /srv/order/submit { cartId:0, addressId, ... }` creates the order. Both submit
 *    and `POST /srv/order/{id}/actions/pay` return a bare `OrderOperationDtoResponse`
 *    ({success,orderId,orderSn,actualPrice,message}) with the outcome on the HTTP status
 *    (201 created / 422 stock / 200 paid / 402 insufficient balance) — NOT an
 *    {errno,errmsg,data} envelope. Placement is a TWO-STEP place->pay flow.
 *  - CJ Dropshipping lines (source 'cj'/'cj_dropshipping' or a 'cj_' goodsId) bypass the
 *    cart and go to `POST /srv/order/cj/orders` by their native productId; the order
 *    service recovers the real CJ vid off the goods_product row.
 */
export type CheckoutPaymentMethod = 'CARD' | 'WALLET';

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
  // Required only when the cart contains CJ lines (CJ createOrder needs a real country).
  country?: string;
  countryCode?: string;
}

export interface PlaceOrderParams {
  items: IItemCart[];
  shipping: ShippingInfo;
  paymentMethod: CheckoutPaymentMethod;
  /** Saved-address id for the local order (omitted -> order service uses the default). */
  addressId?: number;
  couponId?: number;
  userCouponId?: number;
  message?: string;
}

export interface PlacedOrder {
  orderId: number;
  orderSn?: string;
  actualPrice?: number;
  paid?: boolean;
  paymentMethod?: CheckoutPaymentMethod;
  /** Set when CJ Dropshipping lines were placed (pass-through; no local order row). */
  cjOrderNum?: string;
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

const isCjItem = (it: IItemCart): boolean =>
  it.source === 'cj' || it.source === 'cj_dropshipping' || String(it.goodsId ?? '').startsWith('cj_');

/**
 * Step 1 — create the order(s). Local lines create a (not-yet-paid) order via
 * /order/submit; CJ lines place a real CJ dropship order. A stock/validation failure
 * creates no local order.
 */
export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ items, shipping, paymentMethod, addressId, couponId, userCouponId, message }, thunkApi) => {
    const userId = customerUserId();
    if (userId == null) {
      return thunkApi.rejectWithValue({ errno: 401, errmsg: 'Please sign in to place an order', data: null });
    }
    try {
      const localItems = items.filter(it => !isCjItem(it));
      const cjItems = items.filter(isCjItem);

      let placed: PlacedOrder = { orderId: 0, paid: false, paymentMethod };

      // 1) Local lines: mirror the client cart into the server cart, then submit.
      if (localItems.length) {
        await baseAxios.delete(`${BASE_URL_CONTEXT}/cart/items`, { params: { userId } });
        for (const it of localItems) {
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
        const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, submitBody);
        const data = response.data as OrderOperationResponse;
        if (data.success === false || data.orderId == null) {
          return thunkApi.rejectWithValue({ errno: 1, errmsg: data.message ?? 'Order placement failed', data: null });
        }
        placed = { ...placed, orderId: data.orderId, orderSn: data.orderSn, actualPrice: data.actualPrice };
      }

      // 2) CJ lines: pass-through to the CJ dropship order endpoint (real CJ order).
      if (cjItems.length) {
        const lines = cjItems
          .filter(it => it.productId != null)
          .map(it => ({ productId: it.productId, quantity: it.number ?? 1 }));
        if (!lines.length) {
          return thunkApi.rejectWithValue({ errno: 400, errmsg: 'CJ item is missing a product variant — reopen the product and pick a variant.', data: null });
        }
        const cjBody = {
          orderNumber: `CJ-${userId}-${Date.now()}`,
          customerName: shipping.name,
          phone: shipping.mobile,
          countryCode: shipping.countryCode ?? '',
          country: shipping.country ?? '',
          province: shipping.region,
          city: shipping.kommune || shipping.region,
          address: [shipping.address, shipping.addressTwo].filter(Boolean).join(', '),
          zip: shipping.zip,
          remark: '',
          lines,
        };
        try {
          const cjResp = await baseAxios.post(`${BASE_URL_CONTEXT}/order/cj/orders`, cjBody);
          placed = { ...placed, cjOrderNum: cjResp.data?.cjOrderNum ?? cjResp.data?.cjOrderId };
        } catch (cjErr) {
          const prefix = localItems.length ? 'Your local order was placed, but the CJ order failed: ' : 'CJ order failed: ';
          return thunkApi.rejectWithValue({
            errno: (cjErr as { response?: { status?: number } }).response?.status ?? 502,
            errmsg: prefix + messageFromError(cjErr, 'CJ order failed'),
            data: null,
          });
        }
      }

      return placed;
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
 * Step 2 — pay a placed LOCAL order. WALLET debits server-side (insufficient balance ->
 * 402, order left unpaid); CARD carries a Stripe PaymentIntent id (stubbed until real
 * Stripe Elements land). On success the order is PAID.
 */
export const payOrder = createAsyncThunk<PlacedOrder, PayOrderParams, { rejectValue: ApiResult<null> }>(
  'order/pay',
  async ({ orderId, paymentMethod, paymentIntentId }, thunkApi) => {
    try {
      const payload: { paymentMethod: string; paymentIntentId?: string } = {
        paymentMethod: toBackendPaymentMethod(paymentMethod),
      };
      if (paymentMethod === 'CARD') {
        // STUB: no Stripe Elements/keys wired yet, so hand the order service a placeholder
        // confirmed PaymentIntent id. Replace with a real client-confirmed intent later.
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
