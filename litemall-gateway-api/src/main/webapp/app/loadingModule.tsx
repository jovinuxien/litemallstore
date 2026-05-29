import { lazy } from 'react';

export const HomeView = lazy(() => import('app/modules/home/Home'));
export const ProductDetailView = lazy(() => import('app/modules/product/Detail'));
export const CategoryList = lazy(() => import('app/modules/Category/CategoryList'));