import { configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';

import customerAuth from 'app/auth/customerAuthSlice';

/**
 * Customer-only Redux store (one of the two independent stores; the admin
 * store lives in the gateway-admin SPA). Ported customer slices
 * (home/category/product) are registered here as they are migrated.
 */
const store = configureStore({
  reducer: {
    customerAuth,
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