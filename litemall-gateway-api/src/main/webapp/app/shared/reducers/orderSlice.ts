import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { ApiResult, BaseState } from 'app/config/types';
import { IItemCart } from 'app/shared/model/cart/cart.models';

/**
 * Customer order placement. Submits through the gateway to the order service
 * (`POST /srv/order/submit`) as an authenticated customer — baseAxios relays
 * the customer JWT as Bearer; the machine-token relay forwards it downstream.
 *
 * CONTRACT / FOLLOW-UP(order): this codes against the agreed placement contract
 * owned by the `order` worktree's LitemallOrderRestController:
 *   POST /srv/order/submit
 *     body: { items: [{ goodsId, productId, number, price }],
 *             shipping: { name, mobile, email, address, addressTwo, region, kommune, zip },
 *             paymentMethod: 'CARD' | 'WALLET' }
 *     -> { errno, errmsg, data: { orderId, orderSn, actualPrice, paymentMethod } }
 * The order service validates price/stock and reserves stock; a stock/payment
 * failure returns a non-zero errno and creates NO order. If any verb here is
 * missing, it is raised as a follow-up for the `order` worktree (see RUNBOOK).
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

export const placeOrder = createAsyncThunk<PlacedOrder, PlaceOrderParams, { rejectValue: ApiResult<null> }>(
  'order/place',
  async ({ items, shipping, paymentMethod }, thunkApi) => {
    try {
      const payload = {
        items: items.map(it => ({ goodsId: it.goodsId, productId: it.productId, number: it.number, price: it.price })),
        shipping,
        paymentMethod,
      };
      const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, payload);
      if (response.data.errno !== 0) {
        // Stock/payment/balance failure: surface the domain error, place no order.
        return thunkApi.rejectWithValue({ errno: response.data.errno, errmsg: response.data.errmsg, data: null });
      }
      return response.data.data as PlacedOrder;
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
