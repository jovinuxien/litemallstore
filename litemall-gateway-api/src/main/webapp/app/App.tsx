import React from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';

/**
 * Customer storefront shell. The migrated customer modules (home, Category,
 * product, userViews, commonViews — relocated from litemall-all-react-war in
 * Phase 5) are wired into these routes as their imports are normalised onto
 * @litemall/shared. No admin routes here: admin lives in the gateway-admin SPA.
 */
const App: React.FC = () => (
  <BrowserRouter>
    <Routes>
      <Route path="/" element={<div>litemall — customer storefront</div>} />
      <Route path="*" element={<div>Not found</div>} />
    </Routes>
  </BrowserRouter>
);

export default App;