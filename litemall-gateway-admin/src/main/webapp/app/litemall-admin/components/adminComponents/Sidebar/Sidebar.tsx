/* eslint-disable @typescript-eslint/no-explicit-any */
import 'bootstrap/dist/css/bootstrap.min.css';
import classNames from 'classnames';
import * as React from 'react';
import { Nav, NavItem } from 'react-bootstrap';
import { FaCog, FaHome, FaProductHunt, FaUser } from 'react-icons/fa';
import { NavLink } from 'react-router-dom';
import './Sidebar.scss';

interface SidebarProps {
  isOpen: boolean;
  isMinimized: boolean;
  toggleMinimize: () => void;
}
const Sidebar: React.FC<SidebarProps> = ({ isOpen, isMinimized, toggleMinimize }) => {
  const navItems = [
    { name: 'Dashboard', url: 'dashboard', icon: FaHome },
    { name: 'Orders', url: 'orders', icon: FaUser },
    { name: 'goods', url: 'goods', icon: FaProductHunt },
    { name: 'Settings', url: 'settings', icon: FaCog },
  ];

  return (
    <div className={classNames('sidebar', { 'sidebar-minimized': isMinimized, 'sidebar-hidden': !isOpen })}>
      <Nav>
        {navItems.map((item, idx) => (
          <NavItem key={idx}>
            <NavLink to={item.url} className='nav-link'>
              <item.icon className='nav-icon' />
              {!isMinimized && <span>{item.name}</span>}
            </NavLink>
          </NavItem>
        ))}
      </Nav>
      <button className='sidebar-minimizer' onClick={toggleMinimize}>
        <span className='sidebar-minimizer-icon'></span>
      </button>
    </div>
  );
};

export default Sidebar;
