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
import { adminAftersaleApi } from './private/services/adminAftersaleApi';
import { adminCatalogApi } from './private/services/adminCatalogApi';
import { adminContentApi } from './private/services/adminContentApi';
import { adminEngagementApi } from './private/services/adminEngagementApi';
import { adminFreightApi } from './private/services/adminFreightApi';
import { adminParityApi } from './private/services/adminParityApi';
import { adminStoreApi } from './private/services/adminStoreApi';
import { adminOrderCjApi } from './private/services/adminOrderCjApi';
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
  [adminEngagementApi.reducerPath]: adminEngagementApi.reducer,
  [adminAftersaleApi.reducerPath]: adminAftersaleApi.reducer,
  [adminOrderCjApi.reducerPath]: adminOrderCjApi.reducer,
  [adminSysApi.reducerPath]: adminSysApi.reducer,
  [adminFreightApi.reducerPath]: adminFreightApi.reducer,
  [adminStoreApi.reducerPath]: adminStoreApi.reducer,
  [adminContentApi.reducerPath]: adminContentApi.reducer,
  [adminParityApi.reducerPath]: adminParityApi.reducer,
  home,
  product,
  category,
  loadingBar,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default rootReducer;
