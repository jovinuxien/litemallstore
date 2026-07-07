import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { ApiResult, BaseState } from 'app/config/types';
import { cartApi, toReject } from 'app/shared/api';
import { ICartTotalData, IItemCart } from 'app/shared/model/cart/cart.models';

/**
 * Customer cart. Every server call goes through the `/srv` api seam
 * (`cartApi` → order-service `/srv/cart` REST contract), which carries the
 * customer JWT as `Authorization: Bearer` (sessionStorage 'customerToken').
 * No `/wx`.
 *
 * The cart is the single source of truth for the line items the checkout
 * submits. A guest cart is kept in sessionStorage ('cart') and merged with the
 * server cart on fetch so an anonymous customer keeps their basket after login.
 * The order-service /srv/cart endpoints are live — server failures surface as
 * errors rather than being silently degraded.
 */
interface RemoteIndexCartApiResult
  extends ApiResult<{
    cartTotal: ICartTotalData | null;
    cartList: IItemCart[];
  }> {}

interface RemoteAddToCartParams {
  goodsId: number;
  productId: number;
  number: number;
}

const isLoggedIn = (): boolean => !!sessionStorage.getItem('customerToken');

export const fetchCart = createAsyncThunk<RemoteIndexCartApiResult, void, { rejectValue: ApiResult<null> }>('cart/fetchCart', async (_, thunkApi) => {
  // Anonymous customers have only the local cart; skip the server round-trip.
  if (!isLoggedIn()) {
    return { errno: 0, errmsg: '', data: { cartTotal: null, cartList: [] } };
  }
  try {
    const data = await cartApi.list();
    return { errno: 0, errmsg: '', data: { cartTotal: (data?.cartTotal as ICartTotalData) ?? null, cartList: data?.cartList ?? [] } };
  } catch (error) {
    return thunkApi.rejectWithValue(toReject(error));
  }
});

export const remoteAddToCartThunk = createAsyncThunk<unknown, RemoteAddToCartParams, { rejectValue: ApiResult<null> }>(
  'cart/addToCart',
  async ({ goodsId, productId, number }, thunkApi) => {
    try {
      return await cartApi.add({ goodsId, productId, number });
    } catch (error) {
      return thunkApi.rejectWithValue(toReject(error));
    }
  }
);

export const updateCartItem = createAsyncThunk<IItemCart, IItemCart, { rejectValue: ApiResult<null> }>(
  'cart/updateCartItem',
  async (cartItem, { rejectWithValue }) => {
    try {
      if (cartItem.id == null) return cartItem;
      const updated = (await cartApi.update(cartItem.id, cartItem)) as IItemCart | undefined;
      return updated ?? cartItem;
    } catch (err) {
      return rejectWithValue(toReject(err));
    }
  }
);

interface CartState
  extends BaseState<{
    cartTotal: ICartTotalData | null;
    cartList: IItemCart[];
  }> {}

const initialState: CartState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    cartTotal: null,
    cartList: JSON.parse(sessionStorage.getItem('cart') || '[]'),
  },
  errorNumber: null,
};

const persist = (cartList: IItemCart[]) => sessionStorage.setItem('cart', JSON.stringify(cartList));

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    addItem: (state, action: PayloadAction<IItemCart>) => {
      const item = action.payload;
      const existingItem = state.data.cartList.find(it => it.id === item.id);
      if (existingItem) {
        existingItem.number = (existingItem.number ?? 0) + (item.number ?? 0);
      } else {
        state.data.cartList.push(item);
      }
      persist(state.data.cartList);
    },
    updateItem: (state, action: PayloadAction<IItemCart>) => {
      const existingItem = state.data.cartList.find(it => it.id === action.payload.id);
      if (existingItem) {
        existingItem.number = action.payload.number;
      }
      persist(state.data.cartList);
    },
    removeItem: (state, action: PayloadAction<number>) => {
      state.data.cartList = state.data.cartList.filter(item => item.id !== action.payload);
      persist(state.data.cartList);
    },
    clearCart: state => {
      state.data.cartList = [];
      sessionStorage.removeItem('cart');
    },
    syncLocalCart: (state, action: PayloadAction<IItemCart[]>) => {
      state.data.cartList = action.payload;
      persist(action.payload);
    },
  },
  extraReducers: builder => {
    builder
      .addCase(fetchCart.pending, state => {
        state.loading = 'pending';
      })
      .addCase(fetchCart.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? action.error.message ?? 'Failed to load cart';
      })
      .addCase(fetchCart.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const serverCart = action.payload.data.cartList ?? [];
        const localCart: IItemCart[] = JSON.parse(sessionStorage.getItem('cart') || '[]');
        const mergedCart = [...localCart];
        serverCart.forEach(serverItem => {
          const idx = mergedCart.findIndex(item => item.goodsId === serverItem.goodsId);
          if (idx > -1) {
            mergedCart[idx].number = (mergedCart[idx].number ?? 0) + (serverItem.number ?? 0);
          } else {
            mergedCart.push(serverItem);
          }
        });
        state.data = { cartTotal: action.payload.data.cartTotal, cartList: mergedCart };
        persist(mergedCart);
      })
      .addCase(updateCartItem.pending, state => {
        state.loading = 'pending';
      })
      .addCase(updateCartItem.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const index = state.data.cartList.findIndex(item => item.id === action.payload.id);
        if (index !== -1) {
          state.data.cartList[index] = action.payload;
        }
        persist(state.data.cartList);
      })
      .addCase(updateCartItem.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload?.errmsg ?? 'Failed to update cart item';
      });
  },
});

export const { addItem, removeItem, updateItem, clearCart, syncLocalCart } = cartSlice.actions;
export default cartSlice.reducer;
