import React, { lazy, Suspense } from 'react';
import { Spinner } from 'react-bootstrap';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

import Layout from 'app/Layout';
import CustomerProtectedRoute from 'app/shared/auth/CustomerProtectedRoute';

/**
 * Customer storefront routes. The migrated customer modules (home, product
 * listing + facets, product detail, cart, checkout) are wired here. Checkout /
 * orders sit behind CustomerProtectedRoute (customer JWT required). No admin
 * routes — admin lives in the gateway-admin SPA.
 */
const Home = lazy(() => import('app/modules/home/Home'));
const ProductList = lazy(() => import('app/modules/product/List'));
const ProductDetail = lazy(() => import('app/modules/product/Detail'));
const Cart = lazy(() => import('app/views/commonViews/cart/Cart'));
const Checkout = lazy(() => import('app/views/commonViews/cart/Checkout'));
const OrderConfirmation = lazy(() => import('app/views/commonViews/cart/OrderConfirmation'));
const CustomerLogin = lazy(() => import('app/modules/login/CustomerLogin'));

const Loading: React.FC = () => (
  <div className='text-center my-5'>
    <Spinner animation='border' />
  </div>
);

const Orders: React.FC = () => (
  <div className='container my-5'>
    <h2>My orders</h2>
    <p className='text-muted'>Your order history will appear here. (Order-history listing is a follow-up for the order worktree's /srv/order/list endpoint.)</p>
  </div>
);

const App: React.FC = () => (
  <BrowserRouter>
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path='/' element={<Layout />}>
          <Route index element={<Home />} />
          <Route path='products' element={<ProductList />} />
          <Route path='search' element={<ProductList />} />
          <Route path='category/:id' element={<ProductList />} />
          <Route path='product/:id' element={<ProductDetail />} />
          <Route path='cart' element={<Cart />} />
          <Route path='login' element={<CustomerLogin />} />
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
                <Orders />
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
