import { configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';

import customerAuth from 'app/auth/customerAuthSlice';
import category from 'app/modules/Category/categorySlice';
import home from 'app/modules/home/homeSlice';
import productDetail from 'app/modules/product/productDetailSlice';
import product from 'app/modules/product/productSlice';
import search from 'app/modules/product/searchSlice';
import cart from 'app/shared/reducers/cartSlice';
import order from 'app/shared/reducers/orderSlice';

/**
 * Customer-only Redux store (one of the two independent stores; the admin
 * store lives in the gateway-admin SPA). Customer slices migrated in Phase 6
 * (home/category/product/cart + auth) are registered here.
 */
const store = configureStore({
  reducer: {
    customerAuth,
    home,
    category,
    product,
    productDetail,
    search,
    cart,
    order,
  },
  middleware: getDefaultMiddleware =>
    getDefaultMiddleware({
      serializableCheck: {
        ignoredActionPaths: ['payload.config', 'payload.request', 'payload.headers', 'error', 'meta.arg'],
      },
    }),
});

export type IRootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export const useAppSelector: TypedUseSelectorHook<IRootState> = useSelector;
export const useAppDispatch = () => useDispatch<AppDispatch>();

export default store;