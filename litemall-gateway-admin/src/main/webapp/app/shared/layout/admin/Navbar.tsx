import * as React from 'react';
import { Dropdown } from 'react-bootstrap';
import { IconBell, IconMenu } from './icons';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { logoutAdmin } from 'app/shared/reducers/admin-auth';
import { useUnreadNoticeCountQuery } from 'app/shared/reducers/private/services/adminParityApi';
import { toggleSidebar } from 'app/shared/reducers/private/adminUiSlice';
import Breadcrumb from './Breadcrumb';

// Top navbar (mirrors upstream Navbar.vue): hamburger toggles the sidebar,
// breadcrumb on the left, notice bell (unread count from
// /srv/private/admin/profile/nnotice, polled every 60s) and avatar dropdown
// (Home / Logout) on the right. The bell links to the profile page's inbox.
const Navbar: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const adminInfo = useAppSelector(state => state.adminAuth.adminInfo);
  const name = adminInfo?.nickName || 'Admin';
  const { data: unread = 0 } = useUnreadNoticeCountQuery(undefined, { pollingInterval: 60_000 });

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
        <button
          type='button'
          className='btn btn-link p-0 me-3 text-secondary'
          style={{ position: 'relative', lineHeight: 1 }}
          title={unread > 0 ? `${unread} unread notice${unread === 1 ? '' : 's'}` : 'Notices'}
          aria-label='Notices'
          onClick={() => navigate('/admin/profile')}
        >
          <IconBell />
          {unread > 0 && (
            <span
              style={{
                position: 'absolute',
                top: -6,
                right: -8,
                background: '#f56c6c',
                color: '#fff',
                borderRadius: 8,
                fontSize: 10,
                lineHeight: '16px',
                minWidth: 16,
                padding: '0 4px',
                textAlign: 'center',
              }}
            >
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </button>
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
