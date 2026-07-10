import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import ProductCard from 'app/components/userComponents/card/ProductCard';
import { catalogApi } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import 'app/shared/scss/content.scss';

/**
 * Flat listing page reused by Hot deals and New arrivals, modelled on
 * litemall-vue `items/hot` / `items/new`. Sourced from `/srv/goods/list`
 * (isHot/isNew). For keyword/category faceted browsing the InstantSearch
 * `/search` page is used instead.
 */
interface Props {
  mode: 'hot' | 'new';
}

const GoodsListPage: React.FC<Props> = ({ mode }) => {
  const [goods, setGoods] = useState<IGood[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    catalogApi
      .goodsList(mode === 'hot' ? { isHot: true, page: 1, limit: 24 } : { isNew: true, page: 1, limit: 24 })
      .then((res: any) => {
        if (!cancelled) setGoods((res?.list ?? res?.goodsList ?? res ?? []) as IGood[]);
      })
      .catch(() => {
        if (!cancelled) setGoods([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [mode]);

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>{mode === 'hot' ? 'Hot deals' : 'New arrivals'}</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : goods.length === 0 ? (
        <p className='text-muted text-center my-5'>Nothing to show here right now.</p>
      ) : (
        <div className='lm-grid'>
          {goods.map((g, i) => (
            <ProductCard key={(g.id ?? i) as React.Key} product={g} />
          ))}
        </div>
      )}
    </div>
  );
};

export default GoodsListPage;
