import React, { useEffect } from 'react';
import { Route } from 'react-router-dom';
import { AUTHORITIES } from './config/constants';
import { useAppDispatch, useAppSelector } from './config/store';
import { HomeView } from './loadingModule';
import { getFirstCategories } from './modules/Category/categorySlice';
import { getHomeData } from './modules/home/homeSlice';
import Logout from './modules/login/logout';
import { AdminRoutes } from './routes/admin-routes';
import { EntitiesRoutes } from './routes/entities-routes';
import PrivateRoute from './shared/auth/private-route';
import ErrorBoundaryRoutes from './shared/error/error-boundary-routes';
import { CategoryData } from './shared/model/category/category.models';
import SignInView from './views/commonViews/account/SignIn';

interface AppRoutesProps {
  categoryListHome?: CategoryData[];
  homeData?: any; // Placeholder for home data
}

const AppRoutes: React.FC<AppRoutesProps> = ({ categoryListHome, homeData }) => {
  //const homeData = useAppSelector(state => state.home.homeData);
  const homeLoading =  useAppSelector(state => state.home.loading);
  const dispatch = useAppDispatch();

  /*   const { dataCategoryIndex } = useAppSelector(state => state.category.data);
   */

  useEffect(() => {
     // Only fetch if data is missing
   /*  if (!homeData || Object.keys(homeData).length === 0) {
      dispatch(getHomeData());
    } */
    //dispatch(getHomeData());
    //dispatch(getFirstCategories());
  //}, [dispatch, homeData]);
  }, [dispatch]);

  // Only the customer home view depends on backend home-data; gate just that
  // route, not the whole router, so the admin console and the sign-in page stay
  // reachable even before/without the customer catalog backend.
  const homeReady = homeData && Object.keys(homeData).length > 0;

  return (
    <div className='view-routes'>
      <ErrorBoundaryRoutes>
        <Route index
          element={
            homeReady ? <HomeView entities={homeData} categoryListHome={categoryListHome} /> : <div>Loading...</div>
          }
        />

        <Route path='account/signin' element={<SignInView />} />
        <Route path='logout' element={<Logout />} />
        <Route
          path='admin/*'
          element={
            <PrivateRoute hasAnyAuthorities={[AUTHORITIES.ADMIN]}>
              <AdminRoutes />
            </PrivateRoute>
          }
        />
        {/* <Route path='oauth2/authorization/oidc' element={<LoginRedirect />} /> */}
        <Route
          path='*'
          element={
            <PrivateRoute hasAnyAuthorities={[AUTHORITIES.USER]}>
              <EntitiesRoutes />
            </PrivateRoute>
          }
        />
        {/*  <Route path='*' element={<PageNotFound />} /> */}
      </ErrorBoundaryRoutes>
    </div>
  );
};

export default AppRoutes;
