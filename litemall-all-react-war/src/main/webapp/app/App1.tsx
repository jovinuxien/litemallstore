import React, { useEffect, useState } from 'react';
import { Card } from 'react-bootstrap';
import { BrowserRouter } from 'react-router-dom';
import Footer from './components/adminComponents/Footer';
import { AUTHORITIES } from './config/constants';
import { useAppDispatch, useAppSelector } from './config/store';
import { getCatalogData } from './modules/home/homeSlice';
import AppRoutes from './routes';
import { hasAnyAuthority } from './shared/auth/private-route';
import ErrorBoundary from './shared/error/error-boundary';
import Header from './shared/layout/header/header';
import { CategoryData } from './shared/model/category/category.models';
import { getProfile } from './shared/reducers/application-profile';
import { getSession } from './shared/reducers/authentication';

const App = () => {
  const dispatch = useAppDispatch();
  const [categoryListHome, setCategoriesListHome] = useState<CategoryData[]>([]);
  const [categoryListMenu, setCategoriesListMenu] = useState<CategoryData[]>([]);

  useEffect(() => {
    dispatch(getSession()); // For authentication
    dispatch(getProfile()); // For user profile
    getCategories();
  }, [dispatch]);

  const getCategories = () => {
    filterCategoryBySortOrderForHome();
    filterCategoryByOrderForTopMenu();
  };

  const filterCategoryByOrderForTopMenu = () => {
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        setCategoriesListMenu(data.categoryList);
      });
  };

  const filterCategoryBySortOrderForHome = () => {
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        setCategoriesListHome(data.categoryList.slice(0, 3));
      });
  };

  //const currentLocale = useAppSelector(state => state.locale.currentLocale);
  const isAuthenticated = useAppSelector(state => state.authentication.isAuthenticated);
  const isAdmin = useAppSelector(state => hasAnyAuthority(state.authentication.account.authorities, [AUTHORITIES.ADMIN]));
  //const ribbonEnv = useAppSelector(state => state.applicationProfile.ribbonEnv);
  //const isInProduction = useAppSelector(state => state.applicationProfile.inProduction);
  //const isOpenAPIEnabled = useAppSelector(state => state.applicationProfile.isOpenAPIEnabled);

  const paddingTop = '60px';
  return (
    <BrowserRouter>
      <div className='app-container' style={{ paddingTop }}>
        {/*         <ToastContainer position='top-left' className='toastify-container' toastClassName='toastify-toast' />
         */}{' '}
        <ErrorBoundary>
          <Header
            isAuthenticated={isAuthenticated}
            isAdmin={isAdmin}
            //currentLocale={currentLocale}
            //ribbonEnv={ribbonEnv}
            isInProduction={false}
            isOpenAPIEnabled={false}
            currentLocale={''} //categories={categoryListMenu}
          />
        </ErrorBoundary>
        <div className='container-fluid view-container' id='app-view-container'>
          <Card className='jh-card'>
            <ErrorBoundary>
              <AppRoutes />
            </ErrorBoundary>
          </Card>
          <Footer />
        </div>
      </div>
    </BrowserRouter>
  );
};

export default App;
