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
  // Required only when the cart contains CJ lines (CJ createOrder needs a real country).
  country?: string;
  countryCode?: string;
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
  // Set when CJ Dropshipping lines were placed (pass-through; no local order row).
  cjOrderNum?: string;
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
  async ({ items, shipping, paymentMethod }, thunkApi) => {
    const userId = customerUserId();
    if (userId == null) {
      return thunkApi.rejectWithValue({ errno: 401, errmsg: 'Please sign in to place an order', data: null });
    }
    try {
      // Split the cart by source. Local lines go through the Integer-keyed cart +
      // /order/submit; CJ lines (goodsId "cj_<pid>", non-numeric) bypass the cart
      // and go straight to the CJ dropship endpoint with their raw vid.
      const isCjItem = (it: IItemCart) => it.source === 'cj_dropshipping' || String(it.goodsId ?? '').startsWith('cj_');
      const localItems = items.filter(it => !isCjItem(it));
      const cjItems = items.filter(isCjItem);

      let placed: PlacedOrder = { orderId: 0, paymentMethod };

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
        // userId is taken from the gateway header; addressId omitted -> default address.
        const response = await baseAxios.post(`${BASE_URL_CONTEXT}/order/submit`, { cartId: 0, message: '' });
        const data = response.data ?? {};
        if (data.success === false) {
          return thunkApi.rejectWithValue({ errno: 400, errmsg: data.message ?? data.errorCode ?? 'Order placement failed', data: null });
        }
        placed = { ...placed, orderId: data.orderId, orderSn: data.orderSn, actualPrice: data.actualPrice };
      }

      // 2) CJ lines: pass-through to the CJ dropship order endpoint (real CJ order).
      if (cjItems.length) {
        const lines = cjItems
          .filter(it => it.vid)
          .map(it => ({ vid: String(it.vid), quantity: it.number ?? 1 }));
        if (!lines.length) {
          return thunkApi.rejectWithValue({ errno: 400, errmsg: 'CJ item is missing a variant id — reopen the product and pick a variant.', data: null });
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
          const cjMsg = cjErr.response?.data?.message ?? cjErr.message ?? 'CJ order failed';
          // If the local order already committed, say so — it must not look like a total failure.
          const prefix = localItems.length ? 'Your local order was placed, but the CJ order failed: ' : 'CJ order failed: ';
          return thunkApi.rejectWithValue({ errno: cjErr.response?.status ?? 502, errmsg: prefix + cjMsg, data: null });
        }
      }

      return placed;
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
