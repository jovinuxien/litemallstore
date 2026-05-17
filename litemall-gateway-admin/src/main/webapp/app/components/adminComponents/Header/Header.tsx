import * as React from 'react';
import { Badge, Nav, Navbar } from 'react-bootstrap';
/* import { FaBars } from 'react-icons/fa' */;
import HeaderDropdown from './HeaderDropdown';

const SIDEBAR_HIDDEN = 'sidebar-hidden';
const SIDEBAR_MINIMIZED = 'sidebar-minimized';
const SIDEBAR_MOBILE_SHOW = 'sidebar-mobile-show';
const ASIDE_MENU_HIDDEN = 'aside-menu-hidden';

interface HeaderProps {
  toggleSidebar: () => void;
}
const Header: React.FC<HeaderProps> = ({ toggleSidebar }) => {
  const toggleBodyClass = React.useCallback((className: string) => {
    return (e: React.MouseEvent) => {
      e.preventDefault();
      document.body.classList.toggle(className);
    };
  }, []);
  const sidebarToggle = toggleBodyClass(SIDEBAR_HIDDEN);
  const sidebarMinimize = toggleBodyClass(SIDEBAR_MINIMIZED);
  const mobileSidebarToggle = toggleBodyClass(SIDEBAR_MOBILE_SHOW);
  const asideToggle = toggleBodyClass(ASIDE_MENU_HIDDEN);

  return (
    <Navbar expand='lg' className='app-header navbar'>
      <Navbar.Toggle aria-controls='basic-navbar-nav' className='d-lg-none' onClick={mobileSidebarToggle} />
      <Navbar.Brand href='#' />
      <Navbar.Toggle aria-controls='basic-navbar-nav' className='d-md-down-none' onClick={sidebarToggle} />

      <Nav className='d-md-down-none mr-auto'>
        <Nav.Item className='d-md-down-none'>
          <Nav.Link href='#' onClick={toggleSidebar}>
            {/* <FaBars /> */}
          </Nav.Link>
        </Nav.Item>
        <Nav.Item>
          <Nav.Link href='#' className='px-3'>
            Dashboard
          </Nav.Link>
        </Nav.Item>
        <Nav.Item>
          <Nav.Link href='#' className='px-3'>
            Users
          </Nav.Link>
        </Nav.Item>
      </Nav>

      <Nav>
        <Nav.Item className='d-md-down-none'>
          <Nav.Link href='#'>
            <i className='icon-bell' />
            <Badge pill bg='success'>
              5
            </Badge>
          </Nav.Link>
        </Nav.Item>
        <Nav.Item className='d-md-down-none'>
          <Nav.Link href='#'>
            <i className='icon-list' />
          </Nav.Link>
        </Nav.Item>
        <Nav.Item className='d-md-down-none'>
          <Nav.Link href='#'>
            <i className='icon-location-pin' />
          </Nav.Link>
        </Nav.Item>
        <HeaderDropdown />
      </Nav>

      <Navbar.Toggle aria-controls='basic-navbar-nav' className='d-md-down-none' onClick={asideToggle} />
    </Navbar>
  );
};

export default Header;
