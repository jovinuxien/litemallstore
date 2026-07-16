import AdminLayout from 'app/shared/layout/admin/AdminLayout';
import AffiliateDashboard from 'app/views/affiliateViews/Dashboard';
import AffiliateEarnings from 'app/views/affiliateViews/Earnings';
import AffiliateLinks from 'app/views/affiliateViews/Links';
import AffiliateTeam from 'app/views/affiliateViews/Team';
import AffiliateWithdraw from 'app/views/affiliateViews/Withdraw';
import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';

// affiliate-routes.tsx — mounted under `/affiliate/*` behind the AFFILIATE
// PrivateRoute (Wave 5). Reuses the AdminLayout shell; the sidebar/navbar are
// role-aware (menuForAuthorities) so only the affiliate menu shows. Every data
// call goes to /srv/private/affiliate/** as the logged-in promoter — the edge
// rejects any admin surface with 403.
export const AffiliateRoutes = () => (
  <Routes>
    <Route element={<AdminLayout />}>
      <Route index element={<Navigate to='dashboard' replace />} />
      <Route path='dashboard' element={<AffiliateDashboard />} />
      <Route path='links' element={<AffiliateLinks />} />
      <Route path='earnings' element={<AffiliateEarnings />} />
      <Route path='team' element={<AffiliateTeam />} />
      <Route path='withdraw' element={<AffiliateWithdraw />} />
      <Route path='*' element={<Navigate to='dashboard' replace />} />
    </Route>
  </Routes>
);
