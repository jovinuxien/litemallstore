import AdminGoodsList from 'app/views/adminViews/adminModule/Goods/AdminGoodsList';
import GoodsDetail from 'app/views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import GoodsForm from 'app/views/adminViews/adminModule/Goods/GoodsForm';
import Dashboard from 'app/views/adminViews/adminModule/Dashboard/Dashboard';
import StatPage from 'app/views/adminViews/adminModule/Stat/StatPage';
import UserList from 'app/views/adminViews/adminModule/User/UserList';
import AddressList from 'app/views/adminViews/adminModule/User/AddressList';
import BrandList from 'app/views/adminViews/adminModule/Brand/BrandList';
import BrandForm from 'app/views/adminViews/adminModule/Brand/BrandForm';
import CategoryList from 'app/views/adminViews/adminModule/Category/CategoryList';
import CategoryForm from 'app/views/adminViews/adminModule/Category/CategoryForm';
import KeywordList from 'app/views/adminViews/adminModule/Keyword/KeywordList';
import KeywordForm from 'app/views/adminViews/adminModule/Keyword/KeywordForm';
import IssueList from 'app/views/adminViews/adminModule/Issue/IssueList';
import IssueForm from 'app/views/adminViews/adminModule/Issue/IssueForm';
import CommentList from 'app/views/adminViews/adminModule/Comment/CommentList';
import OrderList from 'app/views/adminViews/adminModule/Order/OrderList';
import OrderDetail from 'app/views/adminViews/adminModule/Order/OrderDetail';
import AdList from 'app/views/adminViews/adminModule/Ad/AdList';
import AdForm from 'app/views/adminViews/adminModule/Ad/AdForm';
import CouponList from 'app/views/adminViews/adminModule/Coupon/CouponList';
import CouponForm from 'app/views/adminViews/adminModule/Coupon/CouponForm';
import CouponUserList from 'app/views/adminViews/adminModule/Coupon/CouponUserList';
import GrouponRuleList from 'app/views/adminViews/adminModule/Groupon/GrouponRuleList';
import GrouponRuleForm from 'app/views/adminViews/adminModule/Groupon/GrouponRuleForm';
import GrouponActivityList from 'app/views/adminViews/adminModule/Groupon/GrouponActivityList';
import AdminAccountList from 'app/views/adminViews/adminModule/Sys/AdminAccountList';
import AdminAccountForm from 'app/views/adminViews/adminModule/Sys/AdminAccountForm';
import NoticeList from 'app/views/adminViews/adminModule/Sys/NoticeList';
import NoticeForm from 'app/views/adminViews/adminModule/Sys/NoticeForm';
import LogList from 'app/views/adminViews/adminModule/Sys/LogList';
import RoleList from 'app/views/adminViews/adminModule/Sys/RoleList';
import RoleForm from 'app/views/adminViews/adminModule/Sys/RoleForm';
import StorageList from 'app/views/adminViews/adminModule/Sys/StorageList';
import AdminLayout from 'app/shared/layout/admin/AdminLayout';
import NotAvailable from 'app/shared/layout/admin/NotAvailable';
import { ALL_LEAVES } from 'app/shared/layout/admin/menu.config';
import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

// admin-routes.tsx — mounted under `/admin/*` behind the ADMIN PrivateRoute.
// Everything renders inside the AdminLayout shell (dark sidebar + navbar +
// tags-view). The wired pages (dashboard, goods list, goods detail) use real
// views; every other upstream menu leaf renders the NotAvailable placeholder
// so the menu keeps full fidelity without faking data. Each call still runs as
// an authenticated admin (Bearer admin JWT) via the existing slices/api.

const ADMIN_PREFIX = '/admin/';

// Unwired upstream leaves (User/Mall/Promotion/Sys/Config/Stat + goods
// create/comment) → placeholder. Derived from the single menu config so the
// route table and the sidebar can never drift apart.
const placeholderLeaves = ALL_LEAVES.filter(l => !l.wired && l.path.startsWith(ADMIN_PREFIX) && !l.path.includes(':'));

export const AdminRoutes = () => (
  <Routes>
    <Route element={<AdminLayout />}>
      <Route index element={<Navigate to='dashboard' replace />} />
      <Route path='dashboard' element={<Dashboard />} />
      <Route path='goods' element={<AdminGoodsList />} />
      <Route path='goods/comment' element={<CommentList />} />
      {/* static 'create' wins over the ':id' detail route in v6 ranking */}
      <Route path='goods/create' element={<GoodsForm />} />
      <Route path='goods/:id/edit' element={<GoodsForm />} />
      <Route path='goods/:id' element={<GoodsDetail />} />
      <Route path='user/user' element={<UserList />} />
      <Route path='user/address' element={<AddressList />} />
      <Route path='stat/user' element={<StatPage kind='user' />} />
      <Route path='stat/order' element={<StatPage kind='order' />} />
      <Route path='stat/goods' element={<StatPage kind='goods' />} />
      <Route path='mall/brand' element={<BrandList />} />
      <Route path='mall/brand/create' element={<BrandForm />} />
      <Route path='mall/brand/:id' element={<BrandForm />} />
      <Route path='mall/category' element={<CategoryList />} />
      <Route path='mall/category/create' element={<CategoryForm />} />
      <Route path='mall/category/:id' element={<CategoryForm />} />
      <Route path='mall/keyword' element={<KeywordList />} />
      <Route path='mall/keyword/create' element={<KeywordForm />} />
      <Route path='mall/keyword/:id' element={<KeywordForm />} />
      <Route path='mall/issue' element={<IssueList />} />
      <Route path='mall/issue/create' element={<IssueForm />} />
      <Route path='mall/issue/:id' element={<IssueForm />} />
      <Route path='mall/order' element={<OrderList />} />
      <Route path='mall/order/:id' element={<OrderDetail />} />
      {/* Promotion: ads / coupons / groupon (edge-hosted admin CRUD) */}
      <Route path='promotion/ad' element={<AdList />} />
      <Route path='promotion/ad/create' element={<AdForm />} />
      <Route path='promotion/ad/:id' element={<AdForm />} />
      <Route path='promotion/coupon' element={<CouponList />} />
      <Route path='promotion/coupon/create' element={<CouponForm />} />
      <Route path='promotion/coupon/:id/issued' element={<CouponUserList />} />
      <Route path='promotion/coupon/:id' element={<CouponForm />} />
      <Route path='promotion/groupon-rule' element={<GrouponRuleList />} />
      <Route path='promotion/groupon-rule/create' element={<GrouponRuleForm />} />
      <Route path='promotion/groupon-rule/:id' element={<GrouponRuleForm />} />
      <Route path='promotion/groupon-activity' element={<GrouponActivityList />} />
      {/* System: admins / notices / logs / roles / storage */}
      <Route path='sys/admin' element={<AdminAccountList />} />
      <Route path='sys/admin/create' element={<AdminAccountForm />} />
      <Route path='sys/admin/:id' element={<AdminAccountForm />} />
      <Route path='sys/notice' element={<NoticeList />} />
      <Route path='sys/notice/create' element={<NoticeForm />} />
      <Route path='sys/notice/:id' element={<NoticeForm />} />
      <Route path='sys/log' element={<LogList />} />
      <Route path='sys/role' element={<RoleList />} />
      <Route path='sys/role/create' element={<RoleForm />} />
      <Route path='sys/role/:id' element={<RoleForm />} />
      <Route path='sys/os' element={<StorageList />} />
      {placeholderLeaves.map(leaf => (
        <Route key={leaf.path} path={leaf.path.slice(ADMIN_PREFIX.length)} element={<NotAvailable />} />
      ))}
      <Route path='*' element={<NotAvailable />} />
    </Route>
  </Routes>
);
