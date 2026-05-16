import React, { useEffect, useState } from 'react';
import { Card } from 'react-bootstrap';
import { BrowserRouter } from 'react-router-dom';
import Footer from './components/adminComponents/Footer';
import { AUTHORITIES } from './config/constants';
import { useAppDispatch, useAppSelector } from './config/store';
import { getHomeData } from './modules/home/homeSlice';
import { getFirstCategories } from './modules/Category/categorySlice';
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
  const [homeData, setHomeData] = useState<any>(null); // Add state for homeData


  useEffect(() => {

     const initializedApp = async () => {
      try{
        
        console.log('Initialization Started...');
        // We run in sequence getSession and getProfile because the might depend on each other
       
          // 1. Get home data first (public API)
        console.log("Getting home data...");
        const homeResult = await dispatch(getHomeData()).unwrap();
        console.log("Home data loaded:", homeResult);
        setHomeData(homeResult);


        // 1 Get the session info
        console.log("Getting Session...");
        const sessionResult = await dispatch(getSession());
        console.log("Session Result: ", sessionResult);
        
        // 2 Get the user profile
        console.log("Getting User Profile...");
        const profileResult = await dispatch(getProfile());
        console.log("User Profile Result: ", profileResult);

        // 3 Getting the first 10 categories for home page
        console.log("Getting categories data")
         /* const categoryResult = await Promise.all([
          dispatch(getFirstCategories()).unwrap()
           .then(data => {
            console.log('The new categoryList is:', data.l1CatList);
          })
          // other independent dispatch actions
        ]);  */

        const categoryResult = dispatch(getFirstCategories()).unwrap();

        if((await categoryResult)?.l1CatList) {
         console.log('The new categoryList is:', (await categoryResult)?.l1CatList);
         ((await categoryResult).l1CatList);
         setCategoriesListMenu((await categoryResult).l1CatList);
         setCategoriesListHome((await categoryResult).l1CatList.slice(0, 3));
        }
        //console.log("Categories data:", categoryResult);
      } catch (error) {
        console.error('Initialization Error:', error);
      }

    }; 
    
    //dispatch(getSession()); // For authentication
    //dispatch(getProfile()); // For user profile
    //getCategories();
    //getAllHomeData();

    initializedApp();
  }, [dispatch]);

  const getCategories = () => {
    filterCategoryBySortOrderForHome();
    filterCategoryByOrderForTopMenu();
  };

   const getAllHomeData = () => {
    dispatch(getHomeData())
       .unwrap()
       .then(data => {
        console.log("The homeData gotten are", data)
       })
  } 

  const filterCategoryByOrderForTopMenu = () => {
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        //console.log('Top menu categories:', data.l1CatList);
        console.log('top subCategoryList:', data.l1CatList);
        setCategoriesListMenu(data.l1CatList);
      });
  };

  const filterCategoryBySortOrderForHome = () => {
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        //console.log(data)
        setCategoriesListHome(data.l1CatList?.slice(0, 3));
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
              <AppRoutes categoryListHome={categoryListHome} homeData={homeData} />
            </ErrorBoundary>
          </Card>
          <Footer />
        </div>
      </div>
    </BrowserRouter>
  );
};

export default App;
