import GoodsListView from 'app/views/adminViews/adminModule/Goods';
import GoodsDetails from 'app/views/adminViews/adminModule/Goods/GoodsDetail/GoodsDetail';
import OrdersView from 'app/views/commonViews/account/Orders';
import React from 'react';
import { Route, Routes } from 'react-router-dom';

// admin-routes.tsx
export const AdminRoutes = () => (
  <div className='view-routes'>
    <Routes>
      {/* <Route path='dashboard' element={<DashboardView />} /> */}
      <Route path='orders' element={<OrdersView />} />
      <Route path='goods' element={<GoodsListView />}>
        <Route path=':id/view' element={<GoodsDetails />} />
        {/*  <Route path=':id/edit' element={<GoodsEdit />} /> */}
        {/* Other admin routes */}
      </Route>
    </Routes>
  </div>
);
