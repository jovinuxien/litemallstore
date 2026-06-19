import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';

/**
 * Customer order placement. Submits through the gateway to the order service
 * (`POST /srv/order/submit`) as an authenticated customer — baseAxios relays
 * the customer JWT as Bearer; the machine-token relay forwards it downstream.
 *
 * CONTRACT: verified against the running order service's LitemallOrderRestController.
 * `POST /srv/order/submit` binds the buyer from the gateway-injected `X-User-Id`
 * header (IdentityForwardingFilter, set when the customer Bearer validates) and
 * takes a `LitemallPlaceOrderCommand` body — NOT inline items/shipping:
 *   POST /srv/order/submit
 *     header: X-User-Id (added by the gateway, never sent by the SPA)
 *     body: { cartId, addressId, couponId, userCouponId, message,
 *             grouponRulesId, grouponLinkId }   // cartId 0 == whole checked cart
 *     -> { errno, errmsg, data: { orderId, orderSn, actualPrice } }
 * The order service validates price/stock and reserves stock; a stock failure
 * returns a non-zero errno and creates NO order.
 *
 * FOLLOW-UP(order/user) — blockers for a live end-to-end checkout (see
 * docs/SRV-FOLLOWUPS.md): `addressId` references a saved address, but there is
 * no `/srv/address/*` endpoint yet to create/resolve one; and `paymentMethod`
 * is not a submit field — CARD (Stripe) / WALLET debit is a separate post-order
 * payment action the order worktree still owes. The SPA therefore keeps
 * `paymentMethod` in UI state only and sends the command above.
 */
export type CheckoutPaymentMethod = 'CARD' | 'WALLET';

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
 * `paymentMethod` is carried for the (separate, follow-up) payment step only.
 */
export interface PlaceOrderParams {
  cartId?: number;
  addressId: number;
  couponId?: number;
  userCouponId?: number;
  message?: string;
  paymentMethod: CheckoutPaymentMethod;
}

export interface PlacedOrder {
  orderId: number;
  orderSn?: string;
  actualPrice?: number;
  paymentMethod?: CheckoutPaymentMethod;
}

export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ cartId, addressId, couponId, userCouponId, message, paymentMethod }, thunkApi) => {
    try {
      // LitemallPlaceOrderCommand shape — no userId (gateway injects X-User-Id),
      // no inline items/shipping. Groupon ids default to 0 (not a groupon order).
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
      if (response.data.errno !== 0) {
        // Stock failure: surface the domain error, place no order.
        return thunkApi.rejectWithValue({ errno: response.data.errno, errmsg: response.data.errmsg, data: null });
      }
      // Echo the chosen payment method through for the confirmation/payment step.
      return { ...(response.data.data as PlacedOrder), paymentMethod };
    } catch (error) {
      return thunkApi.rejectWithValue({ errno: 500, errmsg: error.message ?? 'Order placement failed', data: null });
    }
  }
);

interface OrderState extends BaseState<{ lastOrder: PlacedOrder | null }> {}

const initialState: OrderState = {
  loading: 'idle',
  errorMessage: null,
  errorNumber: null,
  data: { lastOrder: null },
};

const orderSlice = createSlice({
  name: 'order',
  initialState,
  reducers: {
    resetOrderState: state => {
      state.loading = 'idle';
      state.errorMessage = null;
      state.errorNumber = null;
    },
  },
  extraReducers: builder => {
    builder
      .addCase(placeOrder.pending, state => {
        state.loading = 'pending';
        state.errorMessage = null;
      })
      .addCase(placeOrder.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        state.data.lastOrder = action.payload;
      })
      .addCase(placeOrder.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Order placement failed';
        state.errorNumber = action.payload?.errno ?? 500;
      });
  },
});

export const { resetOrderState } = orderSlice.actions;
export default orderSlice.reducer;
