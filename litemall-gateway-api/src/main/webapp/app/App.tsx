import React, { lazy, Suspense, useEffect } from 'react';
import { Spinner } from 'react-bootstrap';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';

import Layout from 'app/Layout';
import CustomerProtectedRoute from 'app/shared/auth/CustomerProtectedRoute';
import CookieBanner from 'app/shared/tracking/CookieBanner';
import MatomoTracker from 'app/shared/tracking/MatomoTracker';
import { stashInviteCode } from 'app/shared/util/invite';

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
// Wave 18: public coupon center — claimable coupons, no login needed to browse.
const CouponCenter = lazy(() => import('app/modules/coupon/CouponCenter'));
const Feedback = lazy(() => import('app/modules/user/Feedback'));
const GoodsListPage = lazy(() => import('app/modules/listing/GoodsListPage'));
const DealsPage = lazy(() => import('app/modules/listing/DealsPage'));
const BrandList = lazy(() => import('app/modules/brand/BrandList'));
const BrandDetail = lazy(() => import('app/modules/brand/BrandDetail'));
const TopicList = lazy(() => import('app/modules/topic/TopicList'));
const TopicDetail = lazy(() => import('app/modules/topic/TopicDetail'));
const PageView = lazy(() => import('app/modules/page/PageView'));
const NotFound = lazy(() => import('app/modules/static/NotFound'));
const SeasonRedirect = lazy(() => import('app/modules/page/SeasonRedirect'));
const ArticleList = lazy(() => import('app/modules/article/ArticleList'));
const ArticleDetail = lazy(() => import('app/modules/article/ArticleDetail'));
const Groupon = lazy(() => import('app/modules/groupon/Groupon'));
const GrouponDetail = lazy(() => import('app/modules/groupon/GrouponDetail'));
const Help = lazy(() => import('app/modules/static/Help'));
const CustomerService = lazy(() => import('app/modules/static/CustomerService'));
// Wave-7 Task D: real legal pages. The footer linked all four of these at /help or
// /service until now; they are a launch prerequisite, not decoration.
const Terms = lazy(() => import('app/modules/static/Terms'));
const Privacy = lazy(() => import('app/modules/static/Privacy'));
const Cookies = lazy(() => import('app/modules/static/Cookies'));
const Returns = lazy(() => import('app/modules/static/Returns'));
const Delivery = lazy(() => import('app/modules/static/Delivery'));
const Payments = lazy(() => import('app/modules/static/Payments'));

const Loading: React.FC = () => (
  <div className='text-center my-5'>
    <Spinner animation='border' />
  </div>
);

/**
 * Stashes `?invite=<code>` from ANY landing route into sessionStorage so an
 * affiliate link visitor can browse freely before registering (Wave-5).
 * Register.tsx consumes and clears the stash.
 */
const InviteCapture: React.FC = () => {
  const location = useLocation();
  useEffect(() => {
    const code = new URLSearchParams(location.search).get('invite');
    if (code && code.trim()) {
      stashInviteCode(code.trim());
    }
  }, [location.search]);
  return null;
};

const App: React.FC = () => (
  <BrowserRouter>
    <InviteCapture />
    {/* Wave-6: Matomo page-view tracking. No-op unless /auth/site-config
        carries a tracker URL + site id (and the browser doesn't send DNT).
        Wave-7: and not until the visitor accepts — see CookieBanner. */}
    <MatomoTracker />
    <CookieBanner />
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
          {/* Wave 27: the season collection lives on its own DIY page; this
              legacy path follows whichever season is active (home if none). */}
          <Route path='summer' element={<SeasonRedirect />} />
          <Route path='brands' element={<BrandList />} />
          <Route path='brand/:id' element={<BrandDetail />} />
          <Route path='topics' element={<TopicList />} />
          <Route path='topic/:id' element={<TopicDetail />} />
          {/* Content vertical (goods-management Wave 4): DIY pages + article CMS. */}
          <Route path='page/:id' element={<PageView />} />
          <Route path='articles' element={<ArticleList />} />
          <Route path='article/:id' element={<ArticleDetail />} />
          <Route path='groupon' element={<Groupon />} />
          {/* Wave 21: shareable campaign landing (?join=<leaderPinkId> deep-link). */}
          <Route path='groupon/:id' element={<GrouponDetail />} />
          {/* Wave 18: public coupon center; claiming diverts to /login. */}
          <Route path='coupons' element={<CouponCenter />} />
          <Route path='help' element={<Help />} />
          <Route path='service' element={<CustomerService />} />
          <Route path='terms' element={<Terms />} />
          <Route path='privacy' element={<Privacy />} />
          <Route path='cookies' element={<Cookies />} />
          {/* /returns is the POLICY. /refunds (below) is the customer's own refund
              list and is protected — the two are different pages, not aliases. */}
          <Route path='returns' element={<Returns />} />
          {/* The pages behind the footer's "Tracked delivery" and "Secure payments"
              promises — public, no login, so a shopper can check a claim before buying. */}
          <Route path='delivery' element={<Delivery />} />
          <Route path='payments' element={<Payments />} />
          <Route path='cart' element={<Cart />} />
          <Route path='login' element={<CustomerLogin />} />
          <Route path='register' element={<Register />} />
          {/* Public: the forgot-password tab must work logged-out; the change
              tab checks auth itself. */}
          <Route path='reset' element={<ResetPassword />} />
          {/* Wave 16: checkout is reachable logged-out — the page itself gates
              on auth and offers guest checkout (email ⇒ shadow account) or
              sign-in, instead of a login wall. */}
          <Route path='checkout' element={<Checkout />} />
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
          {/* Retired-catalogue URLs stay in Google's index for weeks after a
              narrowing, so this is a real landing page, not a rare edge case. */}
          <Route path='*' element={<NotFound />} />
        </Route>
      </Routes>
    </Suspense>
  </BrowserRouter>
);

export default App;
