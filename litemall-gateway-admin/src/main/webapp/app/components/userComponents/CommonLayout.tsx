import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getHomeData } from 'app/modules/home/homeSlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import React, { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import './CommonLayout.scss';
import Footer from './Footer';
import Header from './Header';
import { getFirstCategories } from 'app/modules/Category/categorySlice';

const Layout: React.FC = () => {
  const dispatch = useAppDispatch();
  const [categoryListMenu, setCategoryListMenu] = useState([]);
  const [currentCategory, setCurrentCategory] = useState<CategoryData | null>(null);
  const [currentSubCategories, setCurrentSubCategories] = useState<CategoryData[] | null>(null);

  const homeData = useAppSelector(state => state.home.homeData);
  const [categoryListHome, setCategoriesListHome] = useState<CategoryData[]>([]);

  const getCategories = () => {
    filterCategoryBySortOrderForHome();
    filterCategoryByOrderForTopMenu();
  };
  const filterCategoryByOrderForTopMenu = () => {
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        const categoryList = data.l1CatList;
        setCategoryListMenu(categoryList);
      });
  };

  const filterCategoryBySortOrderForHome = () => {
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        const categoryList = data.l1CatList;
        setCategoriesListHome(categoryList.slice(0, 3));
      });
  };

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        setCategoryListMenu(data.l1CatList);
        //setCurrentCategory(data.l1CatList);
        //setCurrentSubCategories(data.subCategoryList);
        //console.log('data from getCatalogData dispatch', data);
      });
  }, [dispatch]);

  return (
    <div className='d-flex flex-column min-vh-100'>
      <Header categoryListMenu={categoryListMenu} currentCategory={currentCategory} currentSubCategories={currentSubCategories} />
      <main className='flex-grow-1 container-fluid px-0'>
        <div className='container py-4'>
          <Outlet />
        </div>
      </main>
      <Footer />
    </div>
  );
};

export default Layout;
