import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getFirstCategories } from 'app/modules/Category/categorySlice';
import { getHomeData } from 'app/modules/home/homeSlice';
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
    dispatch(getFirstCategories())
      .unwrap()
      .then(data => {
        const categoryList = data.l1CatList;
        console.log('Top menu categories:', categoryList);
        console.log('top subCategoryList:', data.l1CatList);
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
