import React from 'react';
import { Outlet } from 'react-router-dom';

const AccountLayout: React.FC = () => {
  return (
    <>
      <main>
        <Outlet />
      </main>
    </>
  );
};

export default AccountLayout;
