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

const isLoggedIn = (): boolean => !!sessionStorage.getItem('customerToken');

/**
 * Is this the same cart line? A cart line is a SKU (productId), not a product (goodsId):
 * matching on goodsId alone collapses "red / 1.8m" and "blue / 2.0m" of one product into
 * a single line and loses one of them.
 *
 * <p>goodsId is compared as a string on purpose — a CJ line's id is `cj_<pid>` and its
 * vid exceeds JS's safe-integer range, so it must never be Number()-coerced (see
 * cart.models.ts).
 */
const sameLine = (a: IItemCart, b: IItemCart): boolean =>
  String(a.goodsId) === String(b.goodsId) && a.productId === b.productId;

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
          const idx = mergedCart.findIndex(item => sameLine(item, serverItem));
          if (idx > -1) {
            // The server line WINS; quantities are not summed.
            //
            // This used to be `local + server`, which was unreachable only because
            // GET /srv/cart/items 400d on every fetch (the client sent no userId and the
            // param was required), so serverCart was always empty. order's Wave-7 fix
            // makes that endpoint work — which would have armed the bug: fetchCart runs on
            // every Cart and Checkout mount, so a surviving server line (e.g. from a
            // failed submit, whose mirror leaves the cart populated) would re-add its
            // quantity on each visit and silently inflate the basket.
            //
            // Summing is wrong regardless of reachability: the mirror COPIES the local
            // cart to the server, so a matching pair is the same line counted twice, not
            // two additions.
            mergedCart[idx] = { ...mergedCart[idx], ...serverItem };
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
