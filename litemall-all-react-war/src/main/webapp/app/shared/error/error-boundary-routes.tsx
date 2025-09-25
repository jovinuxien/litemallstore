import ErrorBoundary from 'app/shared/error/error-boundary';
import React from 'react';
import { Outlet, Route, Routes, RoutesProps } from 'react-router-dom';

const ErrorBoundaryRoutes = ({ children }: RoutesProps) => {
  return (
    <Routes>
      <Route
        element={
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        }
      >
        {children}
      </Route>
    </Routes>
  );
};

export default ErrorBoundaryRoutes;
