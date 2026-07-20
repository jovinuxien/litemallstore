import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { menuForAuthorities } from './menu.config';
import SidebarItem from './SidebarItem';
import TrovemoWordmark from 'app/shared/brand/trovemo-wordmark-dark.svg';
import TrovemoMark from 'app/shared/brand/trovemo-mark.svg';

// The dark fixed sidebar (mirrors upstream views/layout/components/Sidebar).
// Logo on top, then the data-driven menu tree from ADMIN_MENU.
const Sidebar: React.FC = () => {
  const location = useLocation();
  const collapsed = useAppSelector(state => state.adminUi.sidebarCollapsed);
  const authorities = useAppSelector(state => state.adminAuth.authorities);
  const isAffiliate = authorities.includes(AUTHORITIES.AFFILIATE);
  const menu = menuForAuthorities(authorities);

  return (
    <div className='sidebar-container'>
      <div className='sidebar-logo'>
        <span className='sidebar-brand'>
          <TrovemoWordmark className='sidebar-brand-wordmark' aria-label='Trovemo' />
          <span className='sidebar-brand-realm'>{isAffiliate ? 'affiliate' : 'admin'}</span>
        </span>
        <TrovemoMark className='sidebar-brand-mark' aria-label='Trovemo' />
      </div>
      <ul className='el-menu'>
        {menu.map(group => (
          <SidebarItem key={group.key} group={group} activePath={location.pathname} collapsed={collapsed} />
        ))}
      </ul>
    </div>
  );
};

export default Sidebar;
