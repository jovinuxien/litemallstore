/* eslint-disable @typescript-eslint/no-explicit-any */
import classNames from 'classnames';
import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { Badge, Nav, NavItem } from 'reactstrap';

interface AppSidebarNavProps {
  navConfig: {
    items: any[];
  };
}

const AppSidebarNav: React.FC<AppSidebarNavProps> = ({ navConfig }) => {
  const location = useLocation();

  const navLink = (item: any, key: number) => {
    const url = item.url ?? '';
    const classes = classNames('nav-link', item.class);
    return (
      <NavItem key={key}>
        {isExternal(url) ? (
          <a href={url} className={classes} target='_blank' rel='noopener noreferrer'>
            <i className={classNames(item.icon)}></i>
            {item.name}
            {item.badge && (
              <Badge color={item.badge.variant} className='ml-auto'>
                {item.badge.text}
              </Badge>
            )}
          </a>
        ) : (
          <NavLink to={url} className={({ isActive }) => classNames(classes, { active: isActive })}>
            <i className={classNames(item.icon)}></i>
            {item.name}
            {item.badge && (
              <Badge color={item.badge.variant} className='ml-auto'>
                {item.badge.text}
              </Badge>
            )}
          </NavLink>
        )}
      </NavItem>
    );
  };

  const navGroup = (item: any, key: number) => {
    const classes = classNames('nav-dropdown', {
      'nav-dropdown-toggle': !item.disabled,
      open: location.pathname.includes(item.url),
    });
    return (
      <li key={key} className={classes}>
        <a className='nav-link nav-dropdown-toggle' href='#'>
          <i className={classNames(item.icon)}></i>
          {item.name}
        </a>
        <ul className='nav-dropdown-items'>{navList(item.children)}</ul>
      </li>
    );
  };

  const navItem = (item: any, key: number) => {
    const classes = {
      item: classNames(item.class),
      link: classNames('nav-link', item.variant ? `nav-link-${item.variant}` : ''),
      icon: classNames(item.icon),
    };
    return (
      <NavItem key={key} className={classes.item}>
        {navLink(item, key)}
      </NavItem>
    );
  };

  const navList = (items: any[]) => {
    return items.map((item, index) => (item.children ? navGroup(item, index) : navItem(item, index)));
  };

  const isExternal = (url: string) => url.substring(0, 4) === 'http';

  return <Nav vertical>{navList(navConfig.items)}</Nav>;
};

export default AppSidebarNav;
