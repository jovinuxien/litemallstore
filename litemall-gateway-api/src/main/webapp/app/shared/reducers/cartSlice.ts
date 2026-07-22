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
 * submits. The local cart is kept in localStorage ('cart') — NOT sessionStorage,
 * which is per-tab and dies with it: a customer who added goods, closed the
 * browser and came back found "Your cart is empty" with their goods gone. The
 * server cart (written by the checkout mirror) is used as a RESTORE source only:
 * when the local cart is empty and the customer is signed in, their last
 * mirrored basket comes back. When a local cart exists it wins outright —
 * merging the server copy back in resurrected locally-removed lines, because
 * removal never deletes server-side.
 */
interface RemoteIndexCartApiResult
  extends ApiResult<{
    cartTotal: ICartTotalData | null;
    cartList: IItemCart[];
  }> {}

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

/** Stored cart, localStorage first with a one-time sessionStorage migration (pre-fix tabs). */
const readStoredCart = (): IItemCart[] => {
  try {
    const raw = localStorage.getItem('cart') ?? sessionStorage.getItem('cart') ?? '[]';
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
};

const initialState: CartState = {
  loading: 'idle',
  errorMessage: null,
  data: {
    cartTotal: null,
    cartList: readStoredCart(),
  },
  errorNumber: null,
};

const persist = (cartList: IItemCart[]) => localStorage.setItem('cart', JSON.stringify(cartList));

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
      localStorage.removeItem('cart');
      sessionStorage.removeItem('cart'); // pre-localStorage leftover
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
        const localCart = readStoredCart();
        // The LOCAL cart is the source of truth whenever it exists — the server cart is
        // a checkout-mirror byproduct (and holds only the last-mirrored group), so
        // merging it back in resurrected lines the customer had removed locally
        // (removal never deletes server-side) and could re-inflate quantities. The
        // server copy is used for exactly one thing: restoring the basket when the
        // local cart is EMPTY — a new browser, a new device, cleared storage — for a
        // signed-in customer. (Historical note: this merge was dead code until the
        // cartApi.list response-shape fix; the endpoint's bare-array/value-object
        // payload never matched the {cartList} the slice expected, so serverCart was
        // always [].)
        const nextCart = localCart.length > 0 ? localCart : serverCart;
        state.data = { cartTotal: action.payload.data.cartTotal, cartList: nextCart };
        persist(nextCart);
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
