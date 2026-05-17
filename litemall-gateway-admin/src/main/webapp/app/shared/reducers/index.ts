import { ReducersMapObject } from '@reduxjs/toolkit';
import { loadingBarReducer as loadingBar } from 'react-redux-loading-bar';

//import administration from 'app/modules/administration/administration.reducer';
//import applicationProfile from './application-profile';
import category from '../../modules/Category/categorySlice';
import home from '../../modules/home/homeSlice';
import product from '../../modules/product/productSlice';
import authentication from './authentication';
//import locale from './locale';

//import userManagement from './user-management';
/* jhipster-needle-add-reducer-import - JHipster will add reducer here */

const rootReducer: ReducersMapObject = {
  authentication,
  home,
  product,
  category,
  //locale,
  //applicationProfile,
  //administration,
  //userManagement,
  loadingBar,
  /* jhipster-needle-add-reducer-combine - JHipster will add reducer here */
};

export default rootReducer;
