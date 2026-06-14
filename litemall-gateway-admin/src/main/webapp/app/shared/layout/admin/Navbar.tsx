import * as React from 'react';
import { Dropdown } from 'react-bootstrap';
import { IconMenu } from './icons';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { logoutAdmin } from 'app/shared/reducers/admin-auth';
import { toggleSidebar } from 'app/shared/reducers/private/adminUiSlice';
import Breadcrumb from './Breadcrumb';

// Top navbar (mirrors upstream Navbar.vue): hamburger toggles the sidebar,
// breadcrumb on the left, avatar dropdown (Home / Logout) on the right.
const Navbar: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const adminInfo = useAppSelector(state => state.adminAuth.adminInfo);
  const name = adminInfo?.nickName || 'Admin';

  const onLogout = async () => {
    await dispatch(logoutAdmin());
    navigate('/account/signin', { replace: true });
  };

  return (
    <div className='navbar'>
      <button className='hamburger' onClick={() => dispatch(toggleSidebar())} aria-label='Toggle sidebar'>
        <IconMenu />
      </button>

      <Breadcrumb />

      <div className='right-menu'>
        <Dropdown align='end'>
          <Dropdown.Toggle as='div' className='avatar-wrapper' bsPrefix='avatar-wrapper'>
            {adminInfo?.avatarUrl ? (
              <img className='user-avatar' src={adminInfo.avatarUrl} alt={name} />
            ) : (
              <span className='user-avatar' />
            )}
            <span className='user-name'>{name}</span>
          </Dropdown.Toggle>
          <Dropdown.Menu>
            <Dropdown.Item onClick={() => navigate('/')}>Home</Dropdown.Item>
            <Dropdown.Item href='https://github.com/linlinjava/litemall' target='_blank' rel='noreferrer'>
              GitHub
            </Dropdown.Item>
            <Dropdown.Divider />
            <Dropdown.Item onClick={onLogout}>Logout</Dropdown.Item>
          </Dropdown.Menu>
        </Dropdown>
      </div>
    </div>
  );
};

export default Navbar;
