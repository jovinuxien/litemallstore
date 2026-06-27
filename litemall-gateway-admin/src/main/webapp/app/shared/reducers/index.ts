import { ReducersMapObject } from '@reduxjs/toolkit';
import { loadingBarReducer as loadingBar } from 'react-redux-loading-bar';

import category from '../../modules/Category/categorySlice';
import home from '../../modules/home/homeSlice';
import product from '../../modules/product/productSlice';
import authentication from './authentication';
import adminAuth from './admin-auth';
import adminGoods from './private/catalogMgn/adminGoodsSlice';
import adminState from './private/catalogMgn/adminStateSlice';
import adminUi from './private/adminUiSlice';
import { adminGoodsApi } from './private/services/admingoodsrv/adminGoodsApi';
import { adminCatalogApi } from './private/services/adminCatalogApi';

/* jhipster-needle-add-reducer-import - JHipster will add reducer here */

const rootReducer: ReducersMapObject = {
  authentication,
  adminAuth,
  adminGoods,
  adminState,
  adminUi,
  [adminGoodsApi.reducerPath]: adminGoodsApi.reducer,
  [adminCatalogApi.reducerPath]: adminCatalogApi.reducer,
  home,
  product,
  category,
  loadingBar,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default rootReducer;
