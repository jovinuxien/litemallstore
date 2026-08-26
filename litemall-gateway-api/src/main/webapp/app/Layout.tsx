import React, { useEffect, useRef, useState } from 'react';
import { Container, Form, Nav, Navbar, NavDropdown } from 'react-bootstrap';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { logoutCustomerThunk } from 'app/auth/customerAuthSlice';
import CategoryDrawer from 'app/components/commonComponents/CategoryDrawer';
import { RouteErrorBoundary } from 'app/components/commonComponents/ErrorBoundary';
import TrovemoWordmark from 'app/components/commonComponents/TrovemoWordmark';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getCatalogAllData, getCatalogIndexData } from 'app/modules/Category/categorySlice';
import { clearSearchHistory, fetchSearchIndex, ISearchIndexData } from 'app/modules/search/searchIndexApi';
import { SUPPORT_HOURS } from 'app/modules/static/faqData';
import SocialLinks from 'app/shared/config/SocialLinks';
import { useContentAvailability } from 'app/shared/util/useContentAvailability';
import { seasonLabel, seasonPath } from 'app/shared/util/season';
import { useSeason } from 'app/shared/util/useSeason';
import './layout-header.scss';

/**
 * Customer storefront shell, laid out like Amazon's two-row header:
 *  - main bar: text brand, ONE flex-grow search bar (suggest-as-you-type via
 *    /srv/search/suggest, submit routes to /search?q= — the /search page has no
 *    second box, this input is THE search box), then text clusters "Hello, … /
 *    Account & Lists", "Returns & Orders", "Cart (n)".
 *  - secondary strip: the "☰ All" toggle opening the category drawer, plus
 *    text links to the deal/browse pages.
 * Renders the active route via <Outlet/>.
 *
 * The search box carries two dropdowns:
 *  - typing: autocomplete from /srv/search/suggest. Entries follow the Wave-9
 *    typed contract `{text, type: keyword|category|curated, categoryId?}` but
 *    plain strings stay legal — a `category` entry deep-links to the
 *    /category/:id landing, `curated` renders visually distinct, and a plain
 *    string behaves exactly as a keyword.
 *  - focused while empty: "Recent searches" (per-user, with a clear button
 *    wired to POST /srv/search/clearhistory) + "Trending" chips, both from
 *    GET /srv/search/index. Anonymous visitors get trending only.
 */

type SuggestType = 'keyword' | 'category' | 'curated';

interface SuggestEntry {
  text: string;
  type: SuggestType;
  categoryId?: number;
}

// Accept both suggest shapes: the legacy plain string and the Wave-9 typed
// object. Anything malformed collapses to a plain keyword (or is dropped).
const normalizeSuggestEntry = (raw: unknown): SuggestEntry | null => {
  if (typeof raw === 'string') {
    const text = raw.trim();
    return text ? { text, type: 'keyword' } : null;
  }
  if (raw != null && typeof raw === 'object') {
    const o = raw as { text?: unknown; type?: unknown; categoryId?: unknown };
    const text = typeof o.text === 'string' ? o.text.trim() : '';
    if (!text) return null;
    const type: SuggestType = o.type === 'category' || o.type === 'curated' ? o.type : 'keyword';
    const categoryId = Number(o.categoryId);
    return {
      text,
      type,
      ...(type === 'category' && Number.isFinite(categoryId) ? { categoryId } : {}),
    };
  }
  return null;
};

const Layout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const [term, setTerm] = useState('');
  const [suggestions, setSuggestions] = useState<SuggestEntry[]>([]);
  const [open, setOpen] = useState(false);
  const [activeIdx, setActiveIdx] = useState(-1);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [indexData, setIndexData] = useState<ISearchIndexData | null>(null);
  const blurTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Recents change whenever a search is submitted — mark the cached index data
  // stale so the next empty-focus refetches instead of showing old history.
  const indexStale = useRef(true);

  // Wave 26: the narrowed catalogue left these sections with nothing behind
  // them (every seed brand reports zero goods, every seeded topic is empty, the
  // article CMS is empty, no campaign is running). Advertise a section only
  // while it has content — an admin enabling a brand or starting a campaign
  // brings the entry back with no rebuild.
  const hasBrands = useContentAvailability('brands');
  const hasTopics = useContentAvailability('topics');
  const hasArticles = useContentAvailability('articles');
  const hasGroupons = useContentAvailability('groupons');
  // Wave 27: the season collection replaced the hardcoded "Summer Deals" link.
  const season = useSeason();

  const cartCount = useAppSelector(state => state.cart.data.cartList.length);
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);
  const nickName = useAppSelector(state => state.customerAuth.data.userInfo?.nickName);

  // Category data for the "All" drawer (no-ops if the home page loaded it first).
  useEffect(() => {
    dispatch(getCatalogIndexData());
    dispatch(getCatalogAllData());
  }, [dispatch]);

  // Keep the input in sync with the query when landing on /search?q= deep links
  // (this header box is the only search box, so it should show the active term).
  useEffect(() => {
    if (location.pathname === '/search') {
      const q = new URLSearchParams(location.search).get('q');
      if (q != null) setTerm(q);
    }
  }, [location.pathname, location.search]);

  // Debounced autocomplete. /srv/search/suggest returns a raw JSON array whose
  // entries are plain phrases and/or Wave-9 typed objects — both accepted.
  useEffect(() => {
    const q = term.trim();
    if (q.length < 1) {
      setSuggestions([]);
      return undefined;
    }
    let cancelled = false;
    const t = setTimeout(async () => {
      try {
        // NB: the endpoint is /srv/search/suggest (SearchController) — the bare
        // /srv/suggest path never existed and left this autocomplete dead.
        const res = await baseAxios.get(`${BASE_URL_CONTEXT}/search/suggest?q=${encodeURIComponent(q)}`);
        const raw: unknown[] = Array.isArray(res.data) ? res.data : res.data?.data ?? [];
        const arr = raw.map(normalizeSuggestEntry).filter((s): s is SuggestEntry => s != null);
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

  // Search-box chrome (recent + trending) for the focused-while-empty dropdown.
  const loadIndexData = async () => {
    if (indexData != null && !indexStale.current) return;
    try {
      const data = await fetchSearchIndex();
      indexStale.current = false;
      setIndexData(data);
    } catch {
      /* keep whatever chrome we had; the dropdown just shows less */
    }
  };

  const go = (value: string) => {
    const q = value.trim();
    if (!q) return;
    setOpen(false);
    setActiveIdx(-1);
    indexStale.current = true; // this search lands in the history
    navigate(`/search?q=${encodeURIComponent(q)}`);
  };

  // Suggestion click/Enter: category entries deep-link to the category landing
  // (breadcrumb + scoped facets); everything else searches the phrase.
  const pick = (entry: SuggestEntry) => {
    setTerm(entry.text);
    if (entry.type === 'category' && entry.categoryId != null) {
      setOpen(false);
      setActiveIdx(-1);
      navigate(`/category/${entry.categoryId}`);
      return;
    }
    go(entry.text);
  };

  const handleClearHistory = async () => {
    try {
      await clearSearchHistory(); // POST — the backend rejects anonymous callers
      setIndexData(d => (d ? { ...d, historyKeywords: [] } : d));
    } catch {
      /* leave the list; nothing was cleared */
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    go(term);
  };

  const handleFocus = () => {
    if (term.trim().length > 0) {
      if (suggestions.length > 0) setOpen(true);
      return;
    }
    void loadIndexData();
    setOpen(true);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false);
      return;
    }
    if (!open || suggestions.length === 0 || term.trim().length < 1) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActiveIdx(i => Math.min(i + 1, suggestions.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActiveIdx(i => Math.max(i - 1, -1));
    } else if (e.key === 'Enter') {
      if (activeIdx >= 0) {
        e.preventDefault();
        pick(suggestions[activeIdx]);
      }
    }
  };

  // What the empty-focus dropdown can show (history is server-empty for
  // anonymous users; hide the section client-side too so a stale cache from a
  // just-signed-out session never renders).
  const recentKeywords = isAuthenticated ? indexData?.historyKeywords ?? [] : [];
  const trendingKeywords = indexData?.hotKeywords ?? [];
  const showSuggestList = term.trim().length > 0 && suggestions.length > 0;
  const showEmptyChrome = term.trim().length < 1 && (recentKeywords.length > 0 || trendingKeywords.length > 0);

  return (
    <>
      <header className='lm-header sticky-top'>
        {/* Row 1 — brand · search · account/orders/cart (all text). */}
        <Navbar variant='dark' className='lm-header__main'>
          <Container fluid className='flex-wrap gap-2'>
            <Navbar.Brand as={Link} to='/' className='lm-header__brand'>
              <TrovemoWordmark height={30} />
            </Navbar.Brand>

            <Form
              className='lm-header__search d-flex position-relative order-3 order-lg-2 w-100 flex-grow-1'
              onSubmit={handleSearch}
              role='search'
              autoComplete='off'
            >
              <Form.Control
                type='search'
                className='lm-header__search-input'
                placeholder={indexData?.defaultKeyword ? `Search "${indexData.defaultKeyword}" and more…` : 'Search products…'}
                aria-label='Search'
                value={term}
                onChange={e => setTerm(e.target.value)}
                onKeyDown={handleKeyDown}
                onFocus={handleFocus}
                onBlur={() => {
                  blurTimer.current = setTimeout(() => setOpen(false), 150);
                }}
              />
              <button type='submit' className='lm-header__search-btn' aria-label='Search'>
                <i className='bi bi-search' />
              </button>
              {open && showSuggestList && (
                <ul
                  className='list-group position-absolute w-100 shadow'
                  style={{ top: '100%', left: 0, zIndex: 1050, maxHeight: 340, overflowY: 'auto' }}
                  // keep focus on input so onBlur->click ordering works
                  onMouseDown={e => e.preventDefault()}
                >
                  {suggestions.map((s, i) => (
                    <li key={`${s.type}:${s.text}-${i}`}>
                      <button
                        type='button'
                        className={`list-group-item list-group-item-action text-start w-100 lm-header__sug lm-header__sug--${s.type}${
                          i === activeIdx ? ' active' : ''
                        }`}
                        onMouseEnter={() => setActiveIdx(i)}
                        onClick={() => pick(s)}
                      >
                        {s.type === 'category' && <i className='bi bi-grid me-2' aria-hidden='true' />}
                        {s.type === 'curated' && <i className='bi bi-stars me-2' aria-hidden='true' />}
                        <span className='lm-header__sug-text'>{s.text}</span>
                        {s.type === 'category' && <span className='lm-header__sug-hint'>Category</span>}
                        {s.type === 'curated' && <span className='lm-header__sug-hint'>Popular</span>}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {open && showEmptyChrome && (
                <div
                  className='lm-header__dd position-absolute w-100 shadow'
                  style={{ top: '100%', left: 0, zIndex: 1050, maxHeight: 380, overflowY: 'auto' }}
                  // keep focus on input so onBlur->click ordering works
                  onMouseDown={e => e.preventDefault()}
                >
                  {recentKeywords.length > 0 && (
                    <section className='lm-header__dd-section'>
                      <div className='lm-header__dd-head'>
                        <span>Recent searches</span>
                        <button type='button' className='lm-header__dd-clear' onClick={handleClearHistory}>
                          Clear
                        </button>
                      </div>
                      <ul className='lm-header__dd-list'>
                        {recentKeywords.slice(0, 8).map(k => (
                          <li key={k}>
                            <button type='button' className='lm-header__dd-item' onClick={() => go(k)}>
                              <i className='bi bi-clock-history me-2' aria-hidden='true' />
                              {k}
                            </button>
                          </li>
                        ))}
                      </ul>
                    </section>
                  )}
                  {trendingKeywords.length > 0 && (
                    <section className='lm-header__dd-section'>
                      <div className='lm-header__dd-head'>
                        <span>Trending</span>
                      </div>
                      <div className='lm-header__dd-chips'>
                        {trendingKeywords.slice(0, 10).map(k => (
                          <button type='button' key={k} className='lm-header__dd-chip' onClick={() => go(k)}>
                            {k}
                          </button>
                        ))}
                      </div>
                    </section>
                  )}
                </div>
              )}
            </Form>

            <Nav className='lm-header__links ms-auto align-items-center flex-nowrap order-2 order-lg-3'>
              {isAuthenticated ? (
                <NavDropdown
                  align='end'
                  className='lm-header__acct'
                  title={
                    <span className='lm-header__stack'>
                      <small>Hello, {nickName || 'shopper'}</small>
                      <strong>Account &amp; Lists</strong>
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
                  {/* Wave 18: public coupon center (claimable offers). */}
                  <NavDropdown.Item as={Link} to='/coupons'>
                    Coupon center
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
                <NavDropdown
                  align='end'
                  className='lm-header__acct'
                  title={
                    <span className='lm-header__stack'>
                      <small>Hello, sign in</small>
                      <strong>Account &amp; Lists</strong>
                    </span>
                  }
                  id='account-menu'
                >
                  <NavDropdown.Item as={Link} to='/login'>
                    Sign in
                  </NavDropdown.Item>
                  <NavDropdown.Item as={Link} to='/register'>
                    Register
                  </NavDropdown.Item>
                  {/* Wave 18: browsable logged-out; claiming asks to sign in. */}
                  <NavDropdown.Item as={Link} to='/coupons'>
                    Coupon center
                  </NavDropdown.Item>
                </NavDropdown>
              )}
              <Nav.Link as={Link} to='/orders' className='lm-header__stack d-none d-md-flex'>
                <small>Returns</small>
                <strong>&amp; Orders</strong>
              </Nav.Link>
              <Nav.Link as={Link} to='/cart' className='lm-header__cart'>
                <i className='bi bi-cart3' />
                {cartCount > 0 && <span className='lm-header__cart-count'>{cartCount}</span>}
                <strong className='ms-1'>Cart</strong>
              </Nav.Link>
            </Nav>
          </Container>
        </Navbar>

        {/* Row 2 — "☰ All" category-drawer toggle + text links. */}
        <nav className='lm-header__strip'>
          <Container fluid className='d-flex align-items-center gap-1 flex-nowrap overflow-auto'>
            <button
              type='button'
              className='lm-header__all'
              aria-expanded={drawerOpen}
              onClick={() => setDrawerOpen(v => !v)}
            >
              <i className='bi bi-list' /> All
            </button>
            {/* Real discount surface (deal_flag=1) — /hot stays reachable via the hero sidebar. */}
            <Link to='/deals' className='lm-header__strip-link'>
              Today&rsquo;s Deals
            </Link>
            {/* Wave 27: the running season, named by the admin who activated it.
                No season ⇒ no entry — never a link to a collection that isn't. */}
            {season && seasonLabel(season) && (
              <Link to={seasonPath(season)} className='lm-header__strip-link'>
                {seasonLabel(season)}
              </Link>
            )}
            <Link to='/new' className='lm-header__strip-link'>
              New Arrivals
            </Link>
            <Link to='/search' className='lm-header__strip-link'>
              All Products
            </Link>
            {hasBrands && (
              <Link to='/brands' className='lm-header__strip-link'>
                Brands
              </Link>
            )}
            {hasTopics && (
              <Link to='/topics' className='lm-header__strip-link'>
                Topics
              </Link>
            )}
            {hasGroupons && (
              <Link to='/groupon' className='lm-header__strip-link'>
                Group Buys
              </Link>
            )}
            <Link to='/service' className='lm-header__strip-link'>
              Customer Service
            </Link>
          </Container>
        </nav>
      </header>

      <CategoryDrawer show={drawerOpen} onHide={() => setDrawerOpen(false)} />
      <main>
        {/* A crashed page must not take the header, search and nav down with
            it — see components/commonComponents/ErrorBoundary.tsx. */}
        <RouteErrorBoundary>
          <Outlet />
        </RouteErrorBoundary>
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

        {/* Customer-promise strip — every claim here must be one the store can
            keep. It previously promised "Fast, tracked delivery / On every
            order, nationwide" (we sell across EU borders, so no single nation,
            and most stock ships from a supplier warehouse — /help says so in
            plain words), "Hassle-free refunds & exchanges" (the policy has the
            buyer pay return shipping on a change of mind, and offers a
            replacement only for faulty goods) and "every day" support (it is
            weekdays). Each line below now matches the page that governs it. */}
        <div className='bg-dark text-light border-bottom border-secondary'>
          <Container>
            <div className='row text-center py-4 g-3'>
              <div className='col-6 col-md-3'>
                <i className='bi bi-truck fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Tracked delivery</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  Follow every order to your door
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
                <div className='fw-semibold small'>30-day returns</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  Change your mind within 30 days
                </div>
              </div>
              <div className='col-6 col-md-3'>
                <i className='bi bi-headset fs-3 d-block mb-1' />
                <div className='fw-semibold small'>Here to help</div>
                <div className='text-muted' style={{ fontSize: '0.78rem' }}>
                  {SUPPORT_HOURS}
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
                <div className='mb-2 text-white'>
                  <TrovemoWordmark height={26} />
                </div>
                {/* Written for the 17k-product general store this used to be.
                    The catalogue is now deliberately narrowed to the home,
                    garden and tools cluster, and brand attribution is absent on
                    nearly every product — so "thousands of products across home,
                    lifestyle, and more" and "trusted brands" both described a
                    shop that no longer exists. Focus is worth saying out loud. */}
                <p className='text-muted small mb-3'>
                  Everything for the home, garden and workshop — chosen, not scraped. Real prices,
                  a checkout that just works, and 30 days to change your mind. We stand behind
                  every order.
                </p>
                <SocialLinks />
              </div>

              <div className='col-6 col-md-3'>
                <span className='text-uppercase small text-muted d-block mb-2'>Shop</span>
                <div className='d-flex flex-column gap-2'>
                  <Link to='/search' className='link-light text-decoration-none small'>
                    All products
                  </Link>
                  <Link to='/deals' className='link-light text-decoration-none small'>
                    Today&rsquo;s deals
                  </Link>
                  {/* /hot ranks by listed_num — it is a best-seller list, not a
                      discount surface. It was labelled "Hot deals" here and
                      "Today's Deals" in the drawer and on its own heading, which
                      is the header strip's name for /deals: one label, two
                      destinations. "Today's Deals" now means /deals, only. */}
                  <Link to='/hot' className='link-light text-decoration-none small'>
                    Best sellers
                  </Link>
                  <Link to='/new' className='link-light text-decoration-none small'>
                    New arrivals
                  </Link>
                  {hasBrands && (
                    <Link to='/brands' className='link-light text-decoration-none small'>
                      Shop by brand
                    </Link>
                  )}
                  {hasTopics && (
                    <Link to='/topics' className='link-light text-decoration-none small'>
                      Topics &amp; guides
                    </Link>
                  )}
                  {hasArticles && (
                    <Link to='/articles' className='link-light text-decoration-none small'>
                      Articles
                    </Link>
                  )}
                  {hasGroupons && (
                    <Link to='/groupon' className='link-light text-decoration-none small'>
                      Group buys
                    </Link>
                  )}
                  <Link to='/coupons' className='link-light text-decoration-none small'>
                    Coupons &amp; deals
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
                  {/* The policy, not /refunds — that is the signed-in refund list, and a
                      logged-out visitor following a help link should not hit a login wall. */}
                  <Link to='/returns' className='link-light text-decoration-none small'>
                    Returns &amp; refunds
                  </Link>
                  {/* Signed-in only. This sits in the help column, and the
                      Returns link above it exists in its policy form precisely
                      so a logged-out visitor following a help link does not hit
                      a login wall — this link walked them straight into one.
                      Customer service carries the logged-out route (email). */}
                  {isAuthenticated && (
                    <Link to='/user/feedback' className='link-light text-decoration-none small'>
                      Send feedback
                    </Link>
                  )}
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
                © {new Date().getFullYear()} Trovemo. All rights reserved. Prices and availability
                are subject to change.
              </small>
              <div className='d-flex flex-wrap gap-3'>
                <Link to='/terms' className='link-secondary text-decoration-none small'>
                  Conditions of Use
                </Link>
                <Link to='/privacy' className='link-secondary text-decoration-none small'>
                  Privacy Notice
                </Link>
                <Link to='/cookies' className='link-secondary text-decoration-none small'>
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
