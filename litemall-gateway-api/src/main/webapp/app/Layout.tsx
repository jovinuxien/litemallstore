import React, { useState } from 'react';
import { Badge, Container, Form, Nav, Navbar } from 'react-bootstrap';
import { Link, Outlet, useNavigate } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';

/**
 * Customer storefront shell: top nav with brand, suggest-as-you-type-capable
 * search box (routes to /search?q=), cart and account links. Renders the active
 * route via <Outlet/>.
 */
const Layout: React.FC = () => {
  const navigate = useNavigate();
  const [term, setTerm] = useState('');
  const cartCount = useAppSelector(state => state.cart.data.cartList.length);
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    navigate(`/search?q=${encodeURIComponent(term.trim())}`);
  };

  return (
    <>
      <Navbar bg='dark' variant='dark' expand='lg' sticky='top'>
        <Container fluid>
          <Navbar.Brand as={Link} to='/'>
            litemall
          </Navbar.Brand>
          <Navbar.Toggle aria-controls='main-nav' />
          <Navbar.Collapse id='main-nav'>
            <Form className='d-flex mx-auto' style={{ minWidth: '40%' }} onSubmit={handleSearch} role='search'>
              <Form.Control
                type='search'
                placeholder='Search products…'
                aria-label='Search'
                value={term}
                onChange={e => setTerm(e.target.value)}
              />
            </Form>
            <Nav className='ms-auto align-items-center'>
              <Nav.Link as={Link} to='/products'>
                Products
              </Nav.Link>
              <Nav.Link as={Link} to='/cart'>
                <i className='bi bi-cart3' /> Cart{' '}
                {cartCount > 0 && <Badge bg='primary'>{cartCount}</Badge>}
              </Nav.Link>
              <Nav.Link as={Link} to={isAuthenticated ? '/orders' : '/login'}>
                <i className='bi bi-person' /> {isAuthenticated ? 'Account' : 'Sign in'}
              </Nav.Link>
            </Nav>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <main>
        <Outlet />
      </main>
    </>
  );
};

export default Layout;
