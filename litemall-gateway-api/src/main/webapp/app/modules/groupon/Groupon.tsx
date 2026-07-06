import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { contentApi, isMissingEndpoint } from 'app/shared/api';
import 'app/shared/scss/content.scss';

interface GrouponItem {
  id?: number;
  goodsId?: number;
  goodsName?: string;
  picUrl?: string;
  retailPrice?: number | { amount: number };
  grouponPrice?: number | { amount: number };
  discount?: number;
  discountMember?: number;
}

/**
 * Group-buy (groupon) listing, modelled on litemall-vue `items/groupon`.
 * Sourced from `/srv/groupon/list` (promotion service follow-up). Graceful
 * empty until live.
 */
const Groupon: React.FC = () => {
  const [items, setItems] = useState<GrouponItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    contentApi
      .grouponList({ page: 1, limit: 20 })
      .then((res: any) => {
        if (!cancelled) setItems((res?.list ?? res ?? []) as GrouponItem[]);
      })
      .catch(e => {
        if (!cancelled && !isMissingEndpoint(e)) setItems([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>Group deals</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : items.length === 0 ? (
        <p className='text-muted text-center my-5'>No group deals running right now.</p>
      ) : (
        <div className='lm-grid'>
          {items.map((g, i) => (
            <Link key={g.id ?? i} to={`/product/${g.goodsId}`} className='lm-groupon-card'>
              <img src={g.picUrl} alt={g.goodsName} />
              <div className='lm-groupon-card__name'>{g.goodsName}</div>
              <div className='lm-groupon-card__price'>
                <span className='lm-groupon-card__now'>${priceNum(g.grouponPrice ?? g.retailPrice).toFixed(2)}</span>
                {g.discount != null && <span className='lm-groupon-card__badge'>−${g.discount}</span>}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default Groupon;
