import 'bootstrap/dist/css/bootstrap.min.css';
import 'app/sass/adminSass/litemall/admin-theme.scss';

import * as React from 'react';
import { Outlet } from 'react-router-dom';
import { useAppSelector } from 'app/config/store';
import ErrorBoundary from 'app/shared/error/error-boundary';
import Navbar from './Navbar';
import Sidebar from './Sidebar';
import TagsView from './TagsView';

// The admin layout shell — mirrors upstream views/layout/Layout.vue:
//   .app-wrapper → fixed .sidebar-container + .main-container(Navbar + TagsView
//   + AppMain). The routed admin views render into <Outlet/> (the app-main).
const AdminLayout: React.FC = () => {
  const collapsed = useAppSelector(state => state.adminUi.sidebarCollapsed);

  return (
    <div className={`lm-admin app-wrapper${collapsed ? ' is-collapsed' : ''}`}>
      <Sidebar />
      <div className='main-container'>
        <Navbar />
        <TagsView />
        <section className='app-main'>
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </section>
      </div>
    </div>
  );
};

export default AdminLayout;
