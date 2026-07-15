import React, { lazy, Suspense } from 'react';
import { Spinner } from 'react-bootstrap';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';

import Layout from 'app/Layout';
import CustomerProtectedRoute from 'app/shared/auth/CustomerProtectedRoute';

/**
 * Customer storefront routes. The migrated customer modules (home, product
 * listing + facets, product detail, cart, checkout) are wired here. Checkout /
 * orders sit behind CustomerProtectedRoute (customer JWT required). No admin
 * routes — admin lives in the gateway-admin SPA.
 */
const Home = lazy(() => import('app/modules/home/Home'));
const Search = lazy(() => import('app/modules/search/Search'));
const ProductDetail = lazy(() => import('app/modules/product/Detail'));
const Cart = lazy(() => import('app/views/commonViews/cart/Cart'));
const Checkout = lazy(() => import('app/views/commonViews/cart/Checkout'));
const OrderConfirmation = lazy(() => import('app/views/commonViews/cart/OrderConfirmation'));
const CustomerLogin = lazy(() => import('app/modules/login/CustomerLogin'));
const Register = lazy(() => import('app/modules/login/Register'));
const OrderList = lazy(() => import('app/modules/order/OrderList'));
const OrderDetail = lazy(() => import('app/modules/order/OrderDetail'));
const RefundList = lazy(() => import('app/modules/order/RefundList'));
const Payment = lazy(() => import('app/views/commonViews/cart/Payment'));
const PaymentStatus = lazy(() => import('app/views/commonViews/cart/PaymentStatus'));
const UserCenter = lazy(() => import('app/modules/user/UserCenter'));
const Profile = lazy(() => import('app/modules/user/Profile'));
const ResetPassword = lazy(() => import('app/modules/user/ResetPassword'));
const AddressList = lazy(() => import('app/modules/user/AddressList'));
const AddressEdit = lazy(() => import('app/modules/user/AddressEdit'));
const Favorites = lazy(() => import('app/modules/user/Favorites'));
const Footprint = lazy(() => import('app/modules/user/Footprint'));
const Coupons = lazy(() => import('app/modules/user/Coupons'));
const Feedback = lazy(() => import('app/modules/user/Feedback'));
const GoodsListPage = lazy(() => import('app/modules/listing/GoodsListPage'));
const DealsPage = lazy(() => import('app/modules/listing/DealsPage'));
const BrandList = lazy(() => import('app/modules/brand/BrandList'));
const BrandDetail = lazy(() => import('app/modules/brand/BrandDetail'));
const TopicList = lazy(() => import('app/modules/topic/TopicList'));
const TopicDetail = lazy(() => import('app/modules/topic/TopicDetail'));
const PageView = lazy(() => import('app/modules/page/PageView'));
const ArticleList = lazy(() => import('app/modules/article/ArticleList'));
const ArticleDetail = lazy(() => import('app/modules/article/ArticleDetail'));
const Groupon = lazy(() => import('app/modules/groupon/Groupon'));
const Help = lazy(() => import('app/modules/static/Help'));
const CustomerService = lazy(() => import('app/modules/static/CustomerService'));

const Loading: React.FC = () => (
  <div className='text-center my-5'>
    <Spinner animation='border' />
  </div>
);

const App: React.FC = () => (
  <BrowserRouter>
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path='/' element={<Layout />}>
          <Route index element={<Home />} />
          {/* All faceted listing routes resolve to the themed Search page. */}
          {/* InstantSearch faceted search; both the header box and the home/menu
              category tiles resolve here (query-string and /category/:id deep-links). */}
          <Route path='search' element={<Search />} />
          <Route path='category/:id' element={<Search />} />
          <Route path='products' element={<Navigate to='/search' replace />} />
          <Route path='product/:id' element={<ProductDetail />} />
          <Route path='hot' element={<GoodsListPage mode='hot' />} />
          <Route path='new' element={<GoodsListPage mode='new' />} />
          <Route path='deals' element={<DealsPage />} />
          <Route path='brands' element={<BrandList />} />
          <Route path='brand/:id' element={<BrandDetail />} />
          <Route path='topics' element={<TopicList />} />
          <Route path='topic/:id' element={<TopicDetail />} />
          {/* Content vertical (goods-management Wave 4): DIY pages + article CMS. */}
          <Route path='page/:id' element={<PageView />} />
          <Route path='articles' element={<ArticleList />} />
          <Route path='article/:id' element={<ArticleDetail />} />
          <Route path='groupon' element={<Groupon />} />
          <Route path='help' element={<Help />} />
          <Route path='service' element={<CustomerService />} />
          <Route path='cart' element={<Cart />} />
          <Route path='login' element={<CustomerLogin />} />
          <Route path='register' element={<Register />} />
          {/* Public: the forgot-password tab must work logged-out; the change
              tab checks auth itself. */}
          <Route path='reset' element={<ResetPassword />} />
          <Route
            path='checkout'
            element={
              <CustomerProtectedRoute>
                <Checkout />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='order-confirmation/:id'
            element={
              <CustomerProtectedRoute>
                <OrderConfirmation />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='orders'
            element={
              <CustomerProtectedRoute>
                <OrderList />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='order/:id'
            element={
              <CustomerProtectedRoute>
                <OrderDetail />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='refunds'
            element={
              <CustomerProtectedRoute>
                <RefundList />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='pay/:orderId'
            element={
              <CustomerProtectedRoute>
                <Payment />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='pay/:orderId/status'
            element={
              <CustomerProtectedRoute>
                <PaymentStatus />
              </CustomerProtectedRoute>
            }
          />
          {/* Customer account (user center) — all behind the customer JWT. */}
          <Route
            path='user'
            element={
              <CustomerProtectedRoute>
                <UserCenter />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/profile'
            element={
              <CustomerProtectedRoute>
                <Profile />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/address'
            element={
              <CustomerProtectedRoute>
                <AddressList />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/address/:id'
            element={
              <CustomerProtectedRoute>
                <AddressEdit />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/favorites'
            element={
              <CustomerProtectedRoute>
                <Favorites />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/footprint'
            element={
              <CustomerProtectedRoute>
                <Footprint />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/coupons'
            element={
              <CustomerProtectedRoute>
                <Coupons />
              </CustomerProtectedRoute>
            }
          />
          <Route
            path='user/feedback'
            element={
              <CustomerProtectedRoute>
                <Feedback />
              </CustomerProtectedRoute>
            }
          />
          <Route path='*' element={<div className='container my-5'>Page not found</div>} />
        </Route>
      </Routes>
    </Suspense>
  </BrowserRouter>
);

export default App;
