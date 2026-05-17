import { useAppSelector } from 'app/config/store';
import React from 'react';
import { Navigate } from 'react-router-dom';

interface ProtectedRouteProps {
  authType: 'admin' | 'user';
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ authType, children }) => {
  const { isAuthenticatedAdmin, isAuthenticated } = useAppSelector(state => state.auth.data);

  if (authType === 'admin' && !isAuthenticatedAdmin) {
    return <Navigate to='/account/signin' replace />;
  }

  if (authType === 'user' && !isAuthenticated) {
    return <Navigate to='/account/signin' replace />;
  }

  return <>{children}</>;
};

export default ProtectedRoute;
