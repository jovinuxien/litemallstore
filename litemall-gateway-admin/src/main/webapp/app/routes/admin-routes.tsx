import AdminGoodsList from 'app/views/adminViews/adminModule/Goods/AdminGoodsList';
import GoodsDetail from 'app/views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import Dashboard from 'app/views/adminViews/adminModule/Dashboard/Dashboard';
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
      <Route path='goods/:id' element={<GoodsDetail />} />
      {placeholderLeaves.map(leaf => (
        <Route key={leaf.path} path={leaf.path.slice(ADMIN_PREFIX.length)} element={<NotAvailable />} />
      ))}
      <Route path='*' element={<NotAvailable />} />
    </Route>
  </Routes>
);
