import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

/**
 * Gate for customer-only routes (checkout, orders). Reads the customer auth
 * slice; an unauthenticated visitor is sent to /login, preserving the intended
 * destination so login can bounce them back.
 */
const CustomerProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to='/login' replace state={{ from: location }} />;
  }
  return <>{children}</>;
};

export default CustomerProtectedRoute;
