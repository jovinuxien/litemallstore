import React from 'react';
import { Translate } from 'react-jhipster';

import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { NavLink as Link } from 'react-router-dom';
import { NavItem, NavLink, NavbarBrand } from 'reactstrap';
import TrovemoWordmark from 'app/shared/brand/trovemo-wordmark-dark.svg';

export const BrandIcon = props => (
  <div {...props} className='brand-icon'>
    <TrovemoWordmark aria-label='Trovemo' style={{ height: 24, width: 'auto', display: 'block' }} />
  </div>
);

export const Brand = () => (
  <NavbarBrand tag={Link} to='/' className='brand-logo'>
    <BrandIcon />
    <span className='navbar-version'>{VERSION.toLowerCase().startsWith('v') ? VERSION : `v${VERSION}`}</span>
  </NavbarBrand>
);

export const Home = () => (
  <NavItem>
    <NavLink tag={Link} to='/' className='d-flex align-items-center'>
      <FontAwesomeIcon icon='home' />
      <span>
        <Translate contentKey='global.menu.home'>Home</Translate>
      </span>
    </NavLink>
  </NavItem>
);
