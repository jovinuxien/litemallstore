import React, { useCallback, useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { userApi } from 'app/shared/api';
import './user.scss';

interface CollectItem {
  id?: number;
  valueId?: number;
  name?: string;
  goodsName?: string;
  picUrl?: string;
  retailPrice?: number | { amount: number };
  price?: number | { amount: number };
}

/**
 * Favorites / collect, modelled on litemall-vue `user/module-collect` (type 0 =
 * goods). Lists saved products with a remove toggle. Sourced from
 * `/srv/collect/list`.
 */
const Favorites: React.FC = () => {
  const [items, setItems] = useState<CollectItem[]>([]);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res: any = await userApi.collectList(0, { page: 1, limit: 30 });
      setItems((res?.collectList ?? res?.list ?? res ?? []) as CollectItem[]);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const remove = async (valueId?: number) => {
    if (valueId == null) return;
    setItems(prev => prev.filter(i => (i.valueId ?? i.id) !== valueId));
    try {
      await userApi.collectToggle(0, valueId);
    } catch {
      load();
    }
  };

  return (
    <div className='container my-4 lm-user'>
      <h1 className='h4 mb-3'>Favorites</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : items.length === 0 ? (
        <p className='text-muted text-center my-5'>You haven’t saved any products yet.</p>
      ) : (
        <div className='lm-fav-grid'>
          {items.map(it => {
            const pid = it.valueId ?? it.id;
            return (
              <div key={it.id ?? pid} className='lm-fav-card'>
                <Link to={`/product/${pid}`}>
                  <img src={it.picUrl} alt={it.name ?? it.goodsName} />
                  <div className='lm-fav-card__name'>{it.name ?? it.goodsName}</div>
                </Link>
                <div className='lm-fav-card__foot'>
                  <span className='lm-fav-card__price'>${priceNum(it.retailPrice ?? it.price).toFixed(2)}</span>
                  <button type='button' className='lm-fav-card__remove' onClick={() => remove(pid)} aria-label='Remove'>
                    <i className='bi bi-heart-fill' />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default Favorites;
