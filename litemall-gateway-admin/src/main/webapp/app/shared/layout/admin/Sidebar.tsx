import * as React from 'react';
import { useLocation } from 'react-router-dom';
import { AUTHORITIES } from 'app/config/constants';
import { useAppSelector } from 'app/config/store';
import { menuForAuthorities } from './menu.config';
import SidebarItem from './SidebarItem';

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
        <span>{isAffiliate ? 'litemall affiliate' : 'litemall admin'}</span>
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
