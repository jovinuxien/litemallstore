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

  // Check if homeData exists and has content
  if(!homeData || Object.keys(homeData).length === 0){
    return <div>Loading...</div>;  // Return loading message until homeData is available
  }

  return (
    <div className='view-routes'>
      <ErrorBoundaryRoutes>
        <Route index 
          element={
            <HomeView entities={homeData} 
            categoryListHome={categoryListHome}/>
          } 
        />

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
