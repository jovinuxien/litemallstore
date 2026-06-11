import AdminGoodsList from 'app/views/adminViews/adminModule/Goods/AdminGoodsList';
import GoodsDetail from 'app/views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import Dashboard from 'app/views/adminViews/adminModule/Dashboard/Dashboard';
import OrdersView from 'app/views/commonViews/account/Orders';
import React from 'react';
import { Route, Routes } from 'react-router-dom';

// admin-routes.tsx — mounted under `/admin/*` behind the ADMIN PrivateRoute.
// The admin index is the inline goods list; dashboard + per-item detail hang
// off it. Every data call runs as an authenticated admin (Bearer admin JWT).
export const AdminRoutes = () => (
  <div className='view-routes'>
    <Routes>
      <Route index element={<AdminGoodsList />} />
      <Route path='goods' element={<AdminGoodsList />} />
      <Route path='goods/:id' element={<GoodsDetail />} />
      <Route path='dashboard' element={<Dashboard />} />
      <Route path='orders' element={<OrdersView />} />
    </Routes>
  </div>
);
