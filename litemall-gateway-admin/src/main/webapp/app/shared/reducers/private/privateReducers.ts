import { combineReducers } from '@reduxjs/toolkit';
import adminCategory from '../../reducers/private/catalogMgn/adminCategorySlice';
import adminGoodsDetail from '../../reducers/private/catalogMgn/adminGoodsDetailSlice';
import adminGoods from '../../reducers/private/catalogMgn/adminGoodsSlice';
import adminGroupon from '../../reducers/private/catalogMgn/adminGrouponSlice';
import adminOrder from '../../reducers/private/catalogMgn/adminOrderSlice';

const privateReducers = combineReducers({
  adminGoods,
  adminCategory,
  adminGroupon,
  adminOrder,
  adminGoodsDetail,
});

export default privateReducers;
