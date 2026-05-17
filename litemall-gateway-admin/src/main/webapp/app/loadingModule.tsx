import React from 'react';
import Loadable from 'react-loadable';
import Loading from './shared/layout/Loading';

export const HomeView = Loadable({
  loader: () => import('app/modules/home/Home'),
  loading: () => <Loading />,
});

export const ProductDetailView = Loadable({
  loader: () => import('app/modules/product/Detail'),
  loading: () => <Loading />,
});

export const CategoryList = Loadable({
  loader: () => import('app/modules/Category/CategoryList'),
  loading: () => <Loading />,
});
