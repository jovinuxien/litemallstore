import 'bootstrap-icons/font/bootstrap-icons.css';

import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.js';
//import 'bootstrap/dist/js/bootstrap.bundle.min.js';

import 'jquery/dist/jquery.min.js';
//import './App.css';
import './App.min.css';
import './app.scss';

/* import 'bootstrap/js/dist/alert';
import 'bootstrap/js/dist/button';
import 'bootstrap/js/dist/carousel';
import 'bootstrap/js/dist/collapse';
import 'bootstrap/js/dist/dropdown';
import 'bootstrap/js/dist/modal';
import 'bootstrap/js/dist/popover';
import 'bootstrap/js/dist/scrollspy';
import 'bootstrap/js/dist/tab';
import 'bootstrap/js/dist/toast';
import 'bootstrap/js/dist/tooltip'; */
import React, { lazy, Suspense, useEffect, useState } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from './config/store';
import { getCatalogIndexData } from './modules/Category/categorySlice';
import { getCatalogData, getHomeData } from './modules/home/homeSlice';
import { CategoryData } from './shared/model/category/category.models';
import GoodsListView from './views/adminViews/adminModule/Goods';
import GoodsDetails from './views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import ProtectedRoute from './views/commonViews/account/ProtectedRoute';
import AdminLayout from './views/commonViews/layouts/adminLayouts/AdminLayout';
import MainComponentWrapper from './views/commonViews/MainComponentWrapper/MainComponentWrapper';
//import './app.scss';

const DashboardView = lazy(() => import('./views/adminViews/adminModule/Dashboard'));

const Layout = lazy(() => import('./components/userComponents/CommonLayout')); //import Layout from './components/Layout';
const CategoryLayout = lazy(() => import('./components/userComponents/CategoryLayout'));
const OrdersView = lazy(() => import('./views/commonViews/account/Orders'));

const WishlistView = lazy(() => import('./views/commonViews/account/Wishlist'));

const AccountLayout = lazy(() => import('./components/userComponents/AccountLayout'));
const SignInView = lazy(() => import('./views/commonViews/account/SignIn'));
const SignUpView = lazy(() => import('./views/commonViews/account/SignUp'));
const MyProfileView = lazy(() => import('./views/commonViews/account/MyProfile'));
const CheckoutView = lazy(() => import('./views/commonViews/cart/Checkout'));
const BlogDetailView = lazy(() => import('./views/userViews/blog/Detail'));
const BlogView = lazy(() => import('./views/userViews/blog/Blog'));

const ContactUsView = lazy(() => import('./views/userViews/pages/ContactUs'));
const SupportView = lazy(() => import('./views/userViews/pages/Support'));
const CartView = lazy(() => import('./views/commonViews/cart/Cart'));

const StripePaymentView = lazy(() => import('./views/commonViews/payment/PaymentView'));

const Header = lazy(() => import('./components/userComponents/Header'));
const TopMenu = lazy(() => import('./components/userComponents/TopMenu'));
const HomeView = lazy(() => import('./modules/home/Home'));
const Home1View = lazy(() => import('./modules/home/Home1'));

const ProductListView = lazy(() => import('./modules/product/List'));
const ProductDetailView = lazy(() => import('./modules/product/Detail'));

const NotFoundView = lazy(() => import('./views/userViews/pages/404'));
const InternalServerErrorView = lazy(() => import('./views/userViews/pages/500'));
const CategoryList = lazy(() => import('./modules/Category/CategoryList'));
const SubCategoryList = lazy(() => import('./modules/Category/SubCategoryList'));

const App: React.FC = () => {
  const homeData = useAppSelector(state => state.home.homeData);
  const { dataCategoryIndex } = useAppSelector(state => state.category.data);
  const dispatch = useAppDispatch();

  const [categoryListHome, setCategoriesListHome] = useState<CategoryData[]>([]);
  const [categoryListMenu, setCategoriesListMenu] = useState<CategoryData[]>([]);

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getCatalogIndexData());
    getCategories();
  }, []);

  const getCategories = () => {
    filterCategoryBySortOrderForHome();
    filterCategoryByOrderForTopMenu();
  };

  const filterCategoryByOrderForTopMenu = () => {
    dispatch(getCatalogData())
      .unwrap()
      .then(data => {
        const categoryList = data.categoryList;
        setCategoriesListMenu(categoryList);
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

  const router = createBrowserRouter([
    {
      path: '/',
      element: <Layout />,
      children: [
        { index: true, element: <HomeView categoriesListHome={categoryListHome} entities={homeData} /> },
        { path: 'product/:productId', element: <ProductDetailView /> },
        { path: 'cart', element: <CartView /> },
        { path: 'payment', element: <StripePaymentView /> },
        { path: 'checkout', element: <CheckoutView /> },
        { path: 'contact-us', element: <ContactUsView /> },
        { path: 'blog', element: <BlogView data={homeData.banner} /> },
        { path: 'blog/:blogId', element: <BlogDetailView /> },
        { path: 'support', element: <SupportView /> },
        { path: 'account', element: <MyProfileView /> },
        { path: 'account/signin', element: <SignInView /> },
        { path: 'account/signup', element: <SignUpView /> },
        { path: 'account/orders', element: <OrdersView /> },
        { path: 'account/wishlist', element: <WishlistView /> },
        { path: '**', element: <NotFoundView /> },
        {
          path: 'category/:categoryId',
          element: <CategoryLayout />,
          children: [{ index: true, element: <CategoryList /> }],
        },
        {
          path: 'category/:categoryId/:subCategoryId',
          element: <CategoryLayout />,
          children: [{ index: true, element: <SubCategoryList /> }],
        },
      ],
    },
    {
      path: 'private',
      element: (
        <ProtectedRoute authType='admin'>
          <AdminLayout />
        </ProtectedRoute>
      ),
      children: [
        {
          path: 'dashboard',
          element: <DashboardView />,
        },
        {
          path: 'orders',
          element: <DashboardView />,
        },
        {
          path: 'goods',
          element: <MainComponentWrapper MainComponent={GoodsListView} mainRoute={'/private/goods'} />,

          //element: <MainComponentWrapper MainComponent={AdminGoodsList} mainRoute={'/private/goods'} />,
          children: [
            // This is an example of nested routes for managing products
            // And don't forget to add <Outlet/> within DashboardView component
            { index: true, element: <GoodsListView /> },
            { path: ':id/view', element: <GoodsDetails /> },
            { path: ':id/edit', element: <GoodsDetails /> },
            { path: ':id/delete', element: <GoodsDetails /> },
            { path: ':goodsId/variants', element: <ProductDetailView /> },
            { path: ':goodsId/variants/:variantId', element: <ProductDetailView /> },
            { path: ':goodsId/variants/:variantId/edit', element: <ProductDetailView /> },
            { path: ':goodsId/variants/:variantId/delete', element: <ProductDetailView /> },
            { path: ':goodsId/images', element: <ProductDetailView /> },
            { path: ':goodsId/images/:imageId', element: <ProductDetailView /> },
          ],
        },

        {
          path: 'settings',
          element: <DashboardView />,
        },
      ],
    },
  ]);

  return (
    <React.StrictMode>
      <Suspense fallback={<div>Loading...</div>}>
        <RouterProvider router={router} />;
      </Suspense>
    </React.StrictMode>
  );
};

export default App;
