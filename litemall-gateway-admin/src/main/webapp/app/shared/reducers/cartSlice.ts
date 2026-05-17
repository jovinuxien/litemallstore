import { createAsyncThunk, createSlice, PayloadAction } from '@reduxjs/toolkit';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { ApiResult, BaseState } from 'app/config/types';
import { ICartTotalData, IItemCart } from 'app/shared/model/cart/cart.models';
import axios from 'axios';

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

export const fetchCart = createAsyncThunk('cart/fetchCart', async (_, thunkApi) => {
  const currentCatalogUrl = BASE_URL_CONTEXT + '/cart/index';

  const token = sessionStorage.getItem('token');

  if (token) {
    //const userInfo = thunkApi.dispatch(getUserInfo()).unwrap();
    const response = await axios.get<RemoteIndexCartApiResult>(currentCatalogUrl, { headers: { 'X-Litemall-Token': token } });

    console.log(response.data);
    return response.data;
  }
  throw new Error('You need to login first');
});

export const remoteAddToCartThunk = createAsyncThunk('cart/addToCart', async ({ goodsId, productId, number }: RemoteAddToCartParams, thunkApi) => {
  const addToCartUrl = BASE_URL_CONTEXT + '/cart/add';

  const token = sessionStorage.getItem('token');

  if (token) {
    const resultAddedCart = await axios.post(
      addToCartUrl,
      {
        goodsId,
        productId,
        number,
      },
      { headers: { 'X-Litemall-Token': token } }
    );
    //console.log(resultAddedCart.data);
    return resultAddedCart.data;
  }
});

export const updateCartItem = createAsyncThunk('cart/updateCartItem', async (cartItem: IItemCart, { rejectWithValue }) => {
  try {
    const updateCartUrl = BASE_URL_CONTEXT + '/cart/update';
    const token = sessionStorage.getItem('token');

    const response = await axios.put(updateCartUrl, cartItem, { headers: { 'X-Litemall-Token': token } });
    return response.data;
  } catch (err) {
    return rejectWithValue(err.response.data);
  }
});

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
    //We initialize this variable with an empty array
    //Which we a logic to merge the data for async call with the server
    cartList: JSON.parse(sessionStorage.getItem('cart') || '[]'),
  },
  errorNumber: null,
};

const cartSlice = createSlice({
  name: 'cart',
  initialState,
  reducers: {
    addItem: (state, action: PayloadAction<IItemCart>) => {
      const item = action.payload;
      const existingItem = state.data.cartList.find(it => it.id === item.id);
      if (existingItem) {
        existingItem.number += item.number;
        console.log('Cart updated: ', state.data.cartList);
      } else {
        state.data.cartList.push(item);
      }
      sessionStorage.setItem('cart', JSON.stringify(state.data.cartList));
    },

    updateItem: (state, action: PayloadAction<IItemCart>) => {
      const item = action.payload;
      const existingItem = state.data.cartList.find(it => it.id === item.id);
      if (existingItem) {
        existingItem.number = item.number;
      }
      sessionStorage.setItem('cart', JSON.stringify(state.data.cartList));
    },

    removeItem: (state, action: PayloadAction<string>) => {
      state.data.cartList = state.data.cartList.filter(item => item.goodsId !== action.payload);
      sessionStorage.setItem('cart', JSON.stringify(state.data.cartList));
    },

    clearCart: state => {
      state.data.cartList = [];
      sessionStorage.removeItem('cart');
    },

    syncLocalCart: (state, action: PayloadAction<IItemCart[]>) => {
      state.data.cartList = action.payload;
      sessionStorage.setItem('cart', JSON.stringify(action.payload));
    },
  },
  extraReducers: builder => {
    builder
      .addCase(fetchCart.pending, (state, action) => {
        state.loading = 'pending';
      })
      .addCase(fetchCart.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.error.message;
      })
      .addCase(fetchCart.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const serverCart = action.payload.data.cartList;
        const localCart = JSON.parse(sessionStorage.getItem('cart') || '[]');

        const mergedCart = [...localCart];

        serverCart.forEach(serverItem => {
          const existingItemIndex = mergedCart.findIndex(item => item.goodsId === serverItem.goodsId);
          if (existingItemIndex > -1) {
            mergedCart[existingItemIndex].number += serverItem.number;
          } else {
            mergedCart.push(serverItem);
          }
        });
        //state.data = action.payload.data;
        state.data = {
          ...action.payload.data,
          cartList: mergedCart,
        };
        sessionStorage.setItem('cart', JSON.stringify(mergedCart));
      })
      // Update cart item
      .addCase(updateCartItem.pending, state => {
        state.loading = 'pending';
      })
      .addCase(updateCartItem.fulfilled, (state, action) => {
        state.loading = 'succeeded';
        const index = state.data.cartList.findIndex(item => item.id === action.payload.id);
        if (index !== -1) {
          state.data.cartList[index] = action.payload;
        }
      })
      .addCase(updateCartItem.rejected, (state, action) => {
        state.loading = 'failed';
        state.errorMessage = action.payload as string;
      });
  },
});
export const { addItem, removeItem, updateItem, clearCart, syncLocalCart } = cartSlice.actions;
export default cartSlice.reducer;
