import React from 'react';
import { Link, useLocation } from 'react-router-dom';

/**
 * The catch-all route used to be a bare unstyled string: "Page not found", no
 * links, no search, nothing to do next.
 *
 * This is not a rare page. The Wave-26 narrowing took the catalogue from ~17,000
 * on-sale goods to ~3,600, and every retired product URL stays in Google's index
 * for weeks — so a real share of search arrivals land exactly here. A dead end
 * loses a visitor who was already looking for something we sell.
 *
 * It offers a search box seeded with nothing (there is no query to recover from
 * a path) and routes onward into the two anchor surfaces that always have
 * stock. It deliberately does NOT guess what the visitor wanted from the URL:
 * a wrong guess presented confidently is worse than an honest "not here".
 */
const NotFound: React.FC = () => {
  const location = useLocation();
  const [term, setTerm] = React.useState('');

  return (
    <div className='container my-5' style={{ maxWidth: 640 }}>
      <h1 className='h4 mb-2'>We couldn&rsquo;t find that page</h1>
      <p className='text-muted'>
        The address <code>{location.pathname}</code> doesn&rsquo;t exist, or the product that was here is no longer on sale.
      </p>

      <form
        className='d-flex gap-2 my-4'
        onSubmit={e => {
          e.preventDefault();
          const q = term.trim();
          if (q) window.location.assign(`/search?q=${encodeURIComponent(q)}`);
        }}
      >
        <input
          type='search'
          className='form-control'
          value={term}
          onChange={e => setTerm(e.target.value)}
          aria-label='Search products'
          placeholder='Search for a product'
        />
        <button type='submit' className='btn btn-primary'>
          Search
        </button>
      </form>

      <div className='d-flex gap-2 flex-wrap'>
        <Link to='/' className='btn btn-outline-secondary'>
          Home page
        </Link>
        <Link to='/search' className='btn btn-outline-secondary'>
          All products
        </Link>
        <Link to='/deals' className='btn btn-outline-secondary'>
          Today&rsquo;s Deals
        </Link>
      </div>
    </div>
  );
};

export default NotFound;
