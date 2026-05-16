import React from 'react';
import { Outlet, useLocation } from 'react-router-dom';

interface MainComponentWrapperProps {
  MainComponent: React.ComponentType;
  mainRoute: string;
}

const MainComponentWrapper: React.FC<MainComponentWrapperProps> = ({ MainComponent, mainRoute }) => {
  const location = useLocation();
  const isMainRoute = location.pathname === mainRoute;
  return <React.Fragment>{isMainRoute ? <MainComponent /> : <Outlet />}</React.Fragment>;
};

export default MainComponentWrapper;
