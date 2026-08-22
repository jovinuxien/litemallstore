import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';

import ProductCard, { goodId } from 'app/components/userComponents/card/ProductCard';
import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { IGood } from 'app/shared/model/product/product.model';
import 'app/shared/scss/content.scss';

/**
 * Flat listing page for the deal/browse strips, sourced from the OCS-ranked
 * `/srv/search` surface (local + CJ goods, the relevance-boost ranking):
 *   - hot    → "Today's Deals":  bestsellers, sort=-listed_num
 *   - new    → "New Arrivals":   newest first, sort=-created_epoch
 * (The old `/srv/goods/list?isHot|isNew` source was DB-flag-based and
 * local-only.) For keyword/category faceted browsing the InstantSearch
 * `/search` page is used instead.
 */
interface Props {
  mode: 'hot' | 'new';
}

const MODES: Record<Props['mode'], { title: string; params: Record<string, string> }> = {
  hot: { title: 'Today’s Deals', params: { q: '', sort: '-listed_num' } },
  new: { title: 'New Arrivals', params: { q: '', sort: '-created_epoch' } },
};

const GoodsListPage: React.FC<Props> = ({ mode }) => {
  const [goods, setGoods] = useState<IGood[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search`, { params: { ...MODES[mode].params, page: 1, size: 24 } })
      .then(res => {
        const d = res.data?.data ?? res.data ?? {};
        if (!cancelled) setGoods((d.goodsList ?? []) as IGood[]);
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
      <h1 className='h4 mb-3'>{MODES[mode].title}</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : goods.length === 0 ? (
        <p className='text-muted text-center my-5'>Nothing to show here right now.</p>
      ) : (
        <div className='lm-grid'>
          {goods.map((g, i) => (
            <ProductCard key={(goodId(g) ?? i) as React.Key} product={g} />
          ))}
        </div>
      )}
    </div>
  );
};

export default GoodsListPage;
