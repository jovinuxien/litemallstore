import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { contentApi, IBrand, isMissingEndpoint } from 'app/shared/api';
import 'app/shared/scss/content.scss';

/**
 * Brand directory, modelled on litemall-vue `items/brand-list`. Sourced from
 * `/srv/brand/list` (a public endpoint being moved off /wx onto /srv in
 * goods-management). Graceful empty until live.
 */
const BrandList: React.FC = () => {
  const [brands, setBrands] = useState<IBrand[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    contentApi
      .brandList({ page: 1, limit: 30 })
      .then(res => {
        if (!cancelled) setBrands(res?.list ?? []);
      })
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setBrands([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>Brands</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : brands.length === 0 ? (
        <p className='text-muted text-center my-5'>No brands to show yet.</p>
      ) : (
        <div className='lm-brand-grid'>
          {brands.map(b => (
            <Link key={b.id} to={`/brand/${b.id}`} className='lm-brand-tile'>
              <img src={b.picUrl} alt={b.name} />
              <div className='lm-brand-tile__name'>{b.name}</div>
              {b.floorPrice != null && <div className='lm-brand-tile__price'>from ${b.floorPrice}</div>}
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default BrandList;
