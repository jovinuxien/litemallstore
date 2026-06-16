import React, { useEffect, useRef, useState } from 'react';
import { Badge, Container, Form, Nav, Navbar, NavDropdown } from 'react-bootstrap';
import { Link, Outlet, useNavigate } from 'react-router-dom';

import { logoutCustomerThunk } from 'app/auth/customerAuthSlice';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useAppDispatch, useAppSelector } from 'app/config/store';

/**
 * Customer storefront shell: top nav with brand, suggest-as-you-type search box
 * (autocomplete via /srv/suggest, submit routes to /search?q=), cart and account
 * links. Renders the active route via <Outlet/>.
 */
const Layout: React.FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const [term, setTerm] = useState('');
  const [suggestions, setSuggestions] = useState<string[]>([]);
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(-1);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const cartCount = useAppSelector(state => state.cart.data.cartList.length);
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);

  // Debounced autocomplete. /srv/suggest returns a raw JSON array of phrases.
  useEffect(() => {
    const q = term.trim();
    if (q.length < 1) {
      setSuggestions([]);
      setOpen(false);
      return undefined;
    }
    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        const res = await baseAxios.get(`${BASE_URL_CONTEXT}/suggest?q=${encodeURIComponent(q)}`);
        const arr: string[] = Array.isArray(res.data) ? res.data : res.data?.data ?? [];
        if (!cancelled) {
          setSuggestions(arr);
          setActiveIdx(-1);
          setOpen(arr.length > 0);
        }
      } catch {
        if (!cancelled) setSuggestions([]);
      }
    }, 200);
    return () => {
      cancelled = true;
      clearTimeout(t);
    };
  }, [term]);

  const go = (value: string) => {
    const q = value.trim();
    if (!q) return;
    setOpen(false);
    setActiveIdx(-1);
    navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    go(term);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!open || suggestions.length === 0) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIdx(i => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx(i => Math.max(i - 1, -1));
    } else if (e.key === 'Enter') {
      if (activeIdx >= 0) {
        e.preventDefault();
        const picked = suggestions[activeIdx];
        setTerm(picked);
        go(picked);
      }
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
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
            <Form
              className='d-flex mx-auto position-relative'
              style={{ minWidth: '40%' }}
              onSubmit={handleSearch}
              role='search'
              autoComplete='off'
            >
              <Form.Control
                type='search'
                placeholder='Search products…'
                aria-label='Search'
                value={term}
                onChange={e => setTerm(e.target.value)}
                onKeyDown={handleKeyDown}
                onFocus={() => suggestions.length > 0 && setOpen(true)}
                onBlur={() => {
                  blurTimer.current = setTimeout(() => setOpen(false), 150);
                }}
              />
              {open && suggestions.length > 0 && (
                <ul
                  className='list-group position-absolute w-100 shadow'
                  style={{ top: '100%', left: 0, zIndex: 1050, maxHeight: 340, overflowY: 'auto' }}
                  // keep focus on input so onBlur->click ordering works
                  onMouseDown={e => e.preventDefault()}
                >
                  {suggestions.map((s, i) => (
                    <li key={`${s}-${i}`}>
                      <button
                        type='button'
                        className={`list-group-item list-group-item-action text-start w-100${i === activeIdx ? ' active' : ''}`}
                        onMouseEnter={() => setActiveIdx(i)}
                        onClick={() => {
                          setTerm(s);
                          go(s);
                        }}
                      >
                        {s}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </Form>
            <Nav className='ms-auto align-items-center'>
              <Nav.Link as={Link} to='/search'>
                Products
              </Nav.Link>
              <Nav.Link as={Link} to='/cart'>
                <i className='bi bi-cart3' /> Cart{' '}
                {cartCount > 0 && <Badge bg='primary'>{cartCount}</Badge>}
              </Nav.Link>
              {isAuthenticated ? (
                <NavDropdown
                  align='end'
                  title={
                    <span>
                      <i className='bi bi-person' /> Account
                    </span>
                  }
                  id='account-menu'
                >
                  <NavDropdown.Item as={Link} to='/user'>
                    My account
                  </NavDropdown.Item>
                  <NavDropdown.Item as={Link} to='/orders'>
                    My orders
                  </NavDropdown.Item>
                  <NavDropdown.Item as={Link} to='/user/favorites'>
                    Favorites
                  </NavDropdown.Item>
                  <NavDropdown.Item as={Link} to='/user/coupons'>
                    My coupons
                  </NavDropdown.Item>
                  <NavDropdown.Item as={Link} to='/user/address'>
                    Addresses
                  </NavDropdown.Item>
                  <NavDropdown.Divider />
                  <NavDropdown.Item
                    onClick={() => {
                      dispatch(logoutCustomerThunk());
                      navigate('/');
                    }}
                  >
                    Sign out
                  </NavDropdown.Item>
                </NavDropdown>
              ) : (
                <>
                  <Nav.Link as={Link} to='/login'>
                    <i className='bi bi-person' /> Sign in
                  </Nav.Link>
                  <Nav.Link as={Link} to='/register'>
                    Register
                  </Nav.Link>
                </>
              )}
            </Nav>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <main>
        <Outlet />
      </main>
      <footer className='bg-dark text-light mt-5 py-4'>
        <Container>
          <div className='d-flex flex-wrap gap-4 justify-content-between'>
            <div>
              <div className='fw-bold mb-2'>litemall</div>
              <small className='text-muted'>A demo storefront on the litemall customer gateway.</small>
            </div>
            <div className='d-flex gap-4'>
              <div className='d-flex flex-column gap-1'>
                <span className='text-uppercase small text-muted'>Shop</span>
                <Link to='/search' className='link-light text-decoration-none small'>
                  All products
                </Link>
                <Link to='/hot' className='link-light text-decoration-none small'>
                  Hot deals
                </Link>
                <Link to='/new' className='link-light text-decoration-none small'>
                  New arrivals
                </Link>
                <Link to='/brands' className='link-light text-decoration-none small'>
                  Brands
                </Link>
                <Link to='/topics' className='link-light text-decoration-none small'>
                  Topics
                </Link>
              </div>
              <div className='d-flex flex-column gap-1'>
                <span className='text-uppercase small text-muted'>Support</span>
                <Link to='/help' className='link-light text-decoration-none small'>
                  Help center
                </Link>
                <Link to='/service' className='link-light text-decoration-none small'>
                  Customer service
                </Link>
                <Link to='/user/feedback' className='link-light text-decoration-none small'>
                  Send feedback
                </Link>
              </div>
            </div>
          </div>
        </Container>
      </footer>
    </>
  );
};

export default Layout;
