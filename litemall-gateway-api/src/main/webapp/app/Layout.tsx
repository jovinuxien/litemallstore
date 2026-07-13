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
      <footer className='mt-5'>
        {/* Back-to-top bar — Amazon's signature footer affordance. */}
        <button
          type='button'
          className='w-100 border-0 text-white text-center py-3'
          style={{ background: '#0a5d65', fontSize: '0.85rem', letterSpacing: '0.02em' }}
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
        >
          <i className='bi bi-chevron-up me-2' />
          Back to top
        </button>

        {/* Customer-promise strip. */}
        <div className='bg-dark text-light border-bottom border-secondary'>
          <Container>
            <div className='row text-center py-4 g-3'>
              <div className='col-6 col-md-3'>
                <i className='bi bi-truck fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Fast, tracked delivery</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  On every order, nationwide
                </div>
              </div>
              <div className='col-6 col-md-3'>
                <i className='bi bi-shield-lock fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Secure payments</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  Card, Stripe &amp; wallet — encrypted
                </div>
              </div>
              <div className='col-6 col-md-3'>
                <i className='bi bi-arrow-repeat fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Easy returns</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  Hassle-free refunds &amp; exchanges
                </div>
              </div>
              <div className='col-6 col-md-3'>
                <i className='bi bi-headset fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Here to help</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  Customer care, every day
                </div>
              </div>
            </div>
          </Container>
        </div>

        {/* Link columns + company blurb. */}
        <div className='bg-dark text-light py-5'>
          <Container>
            <div className='row g-4'>
              <div className='col-12 col-md-3'>
                <div className='fw-bold fs-5 mb-2'>litemall</div>
                <p className='text-muted small mb-3'>
                  Your everyday marketplace — thousands of products across home, lifestyle, and
                  more, brought together with curated deals, trusted brands, and a checkout that
                  just works. Shop with confidence; we stand behind every order.
                </p>
                <div className='d-flex gap-3 fs-5'>
                  <i className='bi bi-facebook' aria-hidden='true' />
                  <i className='bi bi-instagram' aria-hidden='true' />
                  <i className='bi bi-twitter-x' aria-hidden='true' />
                  <i className='bi bi-youtube' aria-hidden='true' />
                </div>
              </div>

              <div className='col-6 col-md-3'>
                <span className='text-uppercase small text-muted d-block mb-2'>Shop</span>
                <div className='d-flex flex-column gap-2'>
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
                    Shop by brand
                  </Link>
                  <Link to='/topics' className='link-light text-decoration-none small'>
                    Topics &amp; guides
                  </Link>
                  <Link to='/articles' className='link-light text-decoration-none small'>
                    Articles
                  </Link>
                  <Link to='/groupon' className='link-light text-decoration-none small'>
                    Group buys
                  </Link>
                </div>
              </div>

              <div className='col-6 col-md-3'>
                <span className='text-uppercase small text-muted d-block mb-2'>Your account</span>
                <div className='d-flex flex-column gap-2'>
                  <Link to='/user' className='link-light text-decoration-none small'>
                    Account overview
                  </Link>
                  <Link to='/orders' className='link-light text-decoration-none small'>
                    Your orders
                  </Link>
                  <Link to='/user/favorites' className='link-light text-decoration-none small'>
                    Wish list
                  </Link>
                  <Link to='/user/address' className='link-light text-decoration-none small'>
                    Addresses
                  </Link>
                  <Link to='/user/coupons' className='link-light text-decoration-none small'>
                    Coupons &amp; rewards
                  </Link>
                </div>
              </div>

              <div className='col-6 col-md-3'>
                <span className='text-uppercase small text-muted d-block mb-2'>Let us help you</span>
                <div className='d-flex flex-column gap-2'>
                  <Link to='/help' className='link-light text-decoration-none small'>
                    Help center
                  </Link>
                  <Link to='/service' className='link-light text-decoration-none small'>
                    Customer service
                  </Link>
                  <Link to='/orders' className='link-light text-decoration-none small'>
                    Track an order
                  </Link>
                  <Link to='/refunds' className='link-light text-decoration-none small'>
                    Returns &amp; refunds
                  </Link>
                  <Link to='/user/feedback' className='link-light text-decoration-none small'>
                    Send feedback
                  </Link>
                </div>
              </div>
            </div>
          </Container>
        </div>

        {/* Legal / copyright bar. */}
        <div className='bg-black text-muted py-3'>
          <Container>
            <div className='d-flex flex-wrap justify-content-between align-items-center gap-2'>
              <small>
                © {new Date().getFullYear()} litemall. All rights reserved. Prices and availability
                are subject to change.
              </small>
              <div className='d-flex flex-wrap gap-3'>
                <Link to='/help' className='link-secondary text-decoration-none small'>
                  Conditions of Use
                </Link>
                <Link to='/help' className='link-secondary text-decoration-none small'>
                  Privacy Notice
                </Link>
                <Link to='/service' className='link-secondary text-decoration-none small'>
                  Cookie Preferences
                </Link>
                <Link to='/service' className='link-secondary text-decoration-none small'>
                  Contact Us
                </Link>
              </div>
            </div>
          </Container>
        </div>
      </footer>
    </>
  );
};

export default Layout;
