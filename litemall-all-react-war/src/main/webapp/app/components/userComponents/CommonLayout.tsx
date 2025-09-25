import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCatalogData, getHomeData } from 'app/modules/home/homeSlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import React, { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';
import './CommonLayout.scss';
import Footer from './Footer';
import Header from './Header';

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
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        const categoryList = data.categoryList;
        setCategoryListMenu(categoryList);
      });
  };

  const filterCategoryBySortOrderForHome = () => {
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        const categoryList = data.categoryList;
        setCategoriesListHome(categoryList.slice(0, 3));
      });
  };

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        setCategoryListMenu(data.categoryList);
        setCurrentCategory(data.currentCategory);
        setCurrentSubCategories(data.subCategoryList);
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
