import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { userApi } from 'app/shared/api';
import './user.scss';

interface FootprintItem {
  id?: number;
  goodsId?: number;
  name?: string;
  goodsName?: string;
  picUrl?: string;
  retailPrice?: number | { amount: number };
  addTime?: string;
}

/**
 * Browsing history, modelled on litemall-vue footprint. Sourced from
 * `/srv/footprint/list`.
 */
const Footprint: React.FC = () => {
  const [items, setItems] = useState<FootprintItem[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    userApi
      .footprintList({ page: 1, limit: 30 })
      .then((res: any) => {
        if (!cancelled) setItems((res?.list ?? res ?? []) as FootprintItem[]);
      })
      .catch(() => {
        if (!cancelled) setItems([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4 lm-user' style={{ maxWidth: 720 }}>
      <h1 className='h4 mb-3'>Recently viewed</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : items.length === 0 ? (
        <p className='text-muted text-center my-5'>No browsing history yet.</p>
      ) : (
        <div className='d-grid gap-2'>
          {items.map(it => (
            <Link key={it.id} to={`/product/${it.goodsId}`} className='lm-foot-row'>
              <img src={it.picUrl} alt={it.name ?? it.goodsName} />
              <div className='lm-foot-row__info'>
                <div>{it.name ?? it.goodsName}</div>
                {it.addTime && <div className='text-muted small'>{it.addTime}</div>}
              </div>
              <div className='lm-foot-row__price'>${priceNum(it.retailPrice).toFixed(2)}</div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default Footprint;
