import { Action, configureStore, ThunkAction } from '@reduxjs/toolkit';
import profileForm from 'app/components/userComponents/account/FormProfileSlice';
import category from 'app/modules/Category/categorySlice';
import home from 'app/modules/home/homeSlice';
import productDetail from 'app/modules/product/productDetailSlice';
import product from 'app/modules/product/productSlice';
import relatedGoods from 'app/modules/product/relatedSlice';
import cart from 'app/shared/reducers/cartSlice';
import privateReducers from 'app/shared/reducers/private/privateReducers';
//import { adminGoodsApi } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import auth from '../shared/reducers/authSlice';
import profile from '../shared/reducers/profileSlice';
import register from '../shared/reducers/registerSlice';
import errorMiddleware from './middleware/error-middleware';
import loggerMiddleware from './middleware/logger-middleware';
const store = configureStore({
  reducer: {
    home,
    category,
    product,
    productDetail,
    profileForm,
    profile,
    auth,
    cart,
    register,
    relatedGoods,
    private: privateReducers,
    //[adminGoodsApi.reducerPath]: adminGoodsApi.reducer,
  },
  middleware: getDefaultMiddleware =>
    getDefaultMiddleware({
      serializableCheck: false,
      /*serializableCheck: {
       Ignore these field paths in all actions
      ignoredActionPaths: ['payload.config', 'payload.request', 'error', 'meta.arg'],
      } */
      //}).concat(loggerMiddleware, errorMiddleware, adminGoodsApi.middleware),
    }).concat(loggerMiddleware, errorMiddleware),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export type AppThunk<ReturnType = void> = ThunkAction<ReturnType, RootState, unknown, Action<string>>;

export default store;
