import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCatalogData, getHomeData } from 'app/modules/home/homeSlice';
import { CategoryData } from 'app/shared/model/category/category.models';
import React, { useEffect, useState } from 'react';
import { Outlet } from 'react-router-dom';

const CategoryLayout: React.FC = () => {
  const dispatch = useAppDispatch();
  const [categoryListMenu, setCategoryListMenu] = useState([]);

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
        console.log('Top menu categories:', categoryList);
        console.log('top subCategoryList:', data.subCategoryList);
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
      });
  }, [dispatch]);

  return (
    <>
      <main>
        <Outlet />
      </main>
    </>
  );
};

export default CategoryLayout;
