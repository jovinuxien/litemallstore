import React, { useEffect } from 'react';
import { Route } from 'react-router-dom';
import { AUTHORITIES } from './config/constants';
import { useAppDispatch, useAppSelector } from './config/store';
import { HomeView } from './loadingModule';
import { getCatalogIndexData } from './modules/Category/categorySlice';
import { getHomeData } from './modules/home/homeSlice';
import Logout from './modules/login/logout';
import { AdminRoutes } from './routes/admin-routes';
import { EntitiesRoutes } from './routes/entities-routes';
import PrivateRoute from './shared/auth/private-route';
import ErrorBoundaryRoutes from './shared/error/error-boundary-routes';

const AppRoutes = () => {
  const homeData = useAppSelector(state => state.home.homeData);

  /*   const { dataCategoryIndex } = useAppSelector(state => state.category.data);
   */
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getCatalogIndexData());
  }, [dispatch]);

  return (
    <div className='view-routes'>
      <ErrorBoundaryRoutes>
        <Route index element={<HomeView entities={homeData} />} />
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
