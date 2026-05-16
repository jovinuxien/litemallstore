import { ProductDetailView } from 'app/loadingModule';
import CartView from 'app/views/commonViews/cart/Cart';
import React from 'react';
import { Route, Routes } from 'react-router-dom';

// entities-routes.tsx
export const EntitiesRoutes = () => (
  <div className='view-routes'>
    <Routes>
      <Route path='product/:productId' element={<ProductDetailView />} />
      <Route path='cart' element={<CartView />} />
      {/* Add all your public routes here */}
    </Routes>
  </div>
);
