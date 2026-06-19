import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IItemCart } from 'app/shared/model/cart/cart.models';

/**
 * Customer order placement against the CART-BASED order service. baseAxios relays the
 * customer JWT as Bearer; the gateway forwards X-User-Id downstream.
 *
 * The order service does NOT accept an items[] body — it reads the CHECKED rows of the
 * user's SERVER-SIDE cart. Since the SPA keeps a client-side sessionStorage cart, we mirror
 * it into the server cart first, then submit:
 *   DELETE /srv/cart/items?userId            (wipe stale server rows)
 *   POST   /srv/cart/items { userId, goodsId, productId, number, ... }  (one per line, checked)
 *   POST   /srv/order/submit { cartId: 0 }    -> 201 { success, orderId, orderSn, actualPrice }
 * addressId is omitted: the order service falls back to the user's DEFAULT address (the SPA
 * has no address picker yet — /srv/address is the follow-up). CJ items (non-numeric goodsId)
 * can't use the Integer-keyed cart endpoint yet and are skipped. A stock/price/address failure
 * returns a non-2xx and creates NO order.
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

export interface PlaceOrderParams {
  items: IItemCart[];
  shipping: ShippingInfo;
  paymentMethod: CheckoutPaymentMethod;
}

export interface PlacedOrder {
  orderId: number;
  orderSn?: string;
  actualPrice?: number;
  paymentMethod?: CheckoutPaymentMethod;
}

/**
 * Numeric customer id from the JWT `sub` claim (the customer edge issues sub = userId).
 * The order's cart endpoints take userId in the body/param, unlike /order/submit which
 * reads it from the gateway-forwarded X-User-Id header.
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

export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  async ({ items, shipping, paymentMethod }, thunkApi) => {
    const userId = customerUserId();
    if (userId == null) {
      return thunkApi.rejectWithValue({ errno: 401, errmsg: 'Please sign in to place an order', data: null });
    }
    try {
      // Mirror the client-side cart into the server cart the order service reads from:
      // wipe stale rows, then add each line (checked=true). CJ items (non-numeric goodsId)
      // can't use the Integer-keyed cart endpoint yet, so skip them (follow-up).
      await baseAxios.delete(`${BASE_URL_CONTEXT}/cart/items`, { params: { userId } });
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
      }

      // Cart-based submit. userId is taken from the gateway header; addressId omitted ->
      // the order service falls back to the user's default address.
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, { cartId: 0, message: '' });
      const data = response.data ?? {};
      // Order service returns OrderOperationDtoResponse (201), NOT an {errno,data} envelope.
      if (data.success === false) {
        return thunkApi.rejectWithValue({ errno: 400, errmsg: data.message ?? data.errorCode ?? 'Order placement failed', data: null });
      }
      return {
        orderId: data.orderId,
        orderSn: data.orderSn,
        actualPrice: data.actualPrice,
        paymentMethod,
      } as PlacedOrder;
    } catch (error) {
      const resp = error.response?.data;
      return thunkApi.rejectWithValue({
        errno: error.response?.status ?? 500,
        errmsg: resp?.message ?? resp?.errorCode ?? error.message ?? 'Order placement failed',
        data: null,
      });
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
