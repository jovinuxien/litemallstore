import 'bootstrap-icons/font/bootstrap-icons.css';
import React, { useEffect, useState } from 'react';

import Footer from 'app/components/adminComponents/Footer';
import Header from 'app/components/adminComponents/Header';
import Sidebar from 'app/components/adminComponents/Sidebar';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getAdminCategoryList } from 'app/shared/reducers/private/catalogMgn/adminCategorySlice';
import { getAdminGrouponList } from 'app/shared/reducers/private/catalogMgn/adminGrouponSlice';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.js';
import { Breadcrumb, BreadcrumbItem } from 'react-bootstrap';
import { Outlet } from 'react-router-dom';

const AdminLayout: React.FC = () => {
  const dispatch = useAppDispatch();
  const [isOpen, setIsOpen] = useState(true);
  const [isMinimized, setIsMinimized] = useState(false);

  const { adminGoodsResultList, adminGoodsCatAndBrandResult } = useAppSelector(state => state.private.adminGoods.data);
  const { adminCategoryResult } = useAppSelector(state => state.private.adminCategory.data);
  const { adminGrouponResult } = useAppSelector(state => state.private.adminGroupon.data);

  const toggleSidebar = () => {
    setIsOpen(!isOpen);
  };

  useEffect(() => {
    const adminToken = sessionStorage.getItem('adminToken');
    if (adminToken) {
      //dispatch(getAdminGoodsList());
      //dispatch(getAdminGoodsCatAndBrand());
      dispatch(getAdminGrouponList());
      dispatch(getAdminCategoryList());

      console.log('The authenticated admin token is:', adminToken);
    }
  }, []);

  const toggleMinimize = () => {
    setIsMinimized(!isMinimized);
    document.body.classList.toggle('sidebar-minimized');
  };

  return (
    <div className='app'>
      <div className='app-body' style={{ display: 'flex', flexDirection: 'column' }}>
        <Header toggleSidebar={toggleSidebar} />
        <div style={{ display: 'flex', flex: 1 }}>
          <Sidebar isOpen={isOpen} isMinimized={isMinimized} toggleMinimize={toggleMinimize} />
          <main
            className='main'
            style={{
              marginLeft: isOpen ? '250px' : '70px',
              width: '100%',
              transition: 'margin-left 0.3s ease-in-out',
            }}
          >
            <Breadcrumb className='breadcrumb-wrapper'>
              <BreadcrumbItem>Home</BreadcrumbItem>
              <BreadcrumbItem active>{location.pathname}</BreadcrumbItem>
            </Breadcrumb>
            <div className='container-fluid'>
              <div className='animated fadeIn'>
                <Outlet />
              </div>
            </div>
          </main>
        </div>
        <Footer />
      </div>
    </div>
  );
};

export default AdminLayout;
