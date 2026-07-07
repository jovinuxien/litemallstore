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
import { adminPromotionApi } from './private/services/adminPromotionApi';
import { adminStatApi } from './private/services/adminStatApi';
import { adminSysApi } from './private/services/adminSysApi';
import { adminUsersApi } from './private/services/adminUsersApi';

/* jhipster-needle-add-reducer-import - JHipster will add reducer here */

const rootReducer: ReducersMapObject = {
  authentication,
  adminAuth,
  adminGoods,
  adminState,
  adminUi,
  [adminGoodsApi.reducerPath]: adminGoodsApi.reducer,
  [adminCatalogApi.reducerPath]: adminCatalogApi.reducer,
  [adminStatApi.reducerPath]: adminStatApi.reducer,
  [adminUsersApi.reducerPath]: adminUsersApi.reducer,
  [adminPromotionApi.reducerPath]: adminPromotionApi.reducer,
  [adminSysApi.reducerPath]: adminSysApi.reducer,
  home,
  product,
  category,
  loadingBar,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default rootReducer;
