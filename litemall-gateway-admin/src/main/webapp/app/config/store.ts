import { Action, Reducer, ReducersMapObject, Store, ThunkAction, UnknownAction, combineReducers, configureStore } from '@reduxjs/toolkit';
import { TypedUseSelectorHook, useDispatch, useSelector } from 'react-redux';
import { loadingBarMiddleware } from 'react-redux-loading-bar';

import sharedReducers from 'app/shared/reducers';
import { adminGoodsApi } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import { adminCatalogApi } from 'app/shared/reducers/private/services/adminCatalogApi';
import { adminStatApi } from 'app/shared/reducers/private/services/adminStatApi';
import { adminUsersApi } from 'app/shared/reducers/private/services/adminUsersApi';
//import errorMiddleware from './error-middleware';
//import loggerMiddleware from './logger-middleware';
//import notificationMiddleware from './notification-middleware';

const store = configureStore({
  reducer: sharedReducers,
  middleware: getDefaultMiddleware =>
    getDefaultMiddleware({
      serializableCheck: {
        // Ignore these field paths in all actions
        ignoredActionPaths: ['payload.config', 'payload.request', 'payload.headers', 'error', 'meta.arg'],
      },
      //}).concat(errorMiddleware, notificationMiddleware, loadingBarMiddleware(), loggerMiddleware),
    }).concat(loadingBarMiddleware(), adminGoodsApi.middleware, adminCatalogApi.middleware, adminStatApi.middleware, adminUsersApi.middleware),
});

// Allow lazy loading of reducers https://github.com/reduxjs/redux/blob/master/docs/usage/CodeSplitting.md
interface InjectableStore<S = any, A extends Action = UnknownAction> extends Store<S, A> {
  asyncReducers: ReducersMapObject;
  injectReducer(key: string, reducer: Reducer): void;
}

export function configureInjectableStore(storeToInject) {
  const injectableStore = storeToInject as InjectableStore<any, any>;
  injectableStore.asyncReducers = {};

  injectableStore.injectReducer = (key, asyncReducer) => {
    injectableStore.asyncReducers[key] = asyncReducer;
    injectableStore.replaceReducer(
      combineReducers({
        ...sharedReducers,
        ...injectableStore.asyncReducers,
      })
    );
  };

  return injectableStore;
}

const injectableStore = configureInjectableStore(store);

const getStore = () => injectableStore;

export type IRootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export const useAppSelector: TypedUseSelectorHook<IRootState> = useSelector;
export const useAppDispatch = () => useDispatch<AppDispatch>();
export type AppThunk<ReturnType = void> = ThunkAction<ReturnType, IRootState, unknown, UnknownAction>;

export default getStore;
