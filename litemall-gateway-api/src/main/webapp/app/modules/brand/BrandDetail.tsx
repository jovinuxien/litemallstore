import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link, useParams } from 'react-router-dom';

import ProductCard from 'app/components/userComponents/card/ProductCard';
import { catalogApi, contentApi, IBrand, isMissingEndpoint } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import 'app/shared/scss/content.scss';

/**
 * Brand storefront, modelled on litemall-vue `items/brand`: the brand header
 * plus its products (via `/srv/goods/list?brandId=`). Brand meta from
 * `/srv/brand/detail`. Graceful when endpoints aren't live.
 */
const BrandDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const [brand, setBrand] = useState<IBrand | null>(null);
  const [goods, setGoods] = useState<IGood[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!id) return;
    let cancelled = false;
    setLoading(true);
    Promise.allSettled([contentApi.brandDetail(id), catalogApi.goodsList({ brandId: id, page: 1, limit: 24 })])
      .then(([b, g]) => {
        if (cancelled) return;
        if (b.status === 'fulfilled') setBrand(b.value ?? null);
        if (g.status === 'fulfilled') {
          const res: any = g.value;
          setGoods((res?.list ?? res?.goodsList ?? res ?? []) as IGood[]);
        }
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (loading) {
    return (
      <div className='text-center my-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  return (
    <div className='container my-4'>
      <Link to='/brands' className='btn btn-link px-0 mb-2'>
        <i className='bi bi-chevron-left' /> All brands
      </Link>

      {brand && (
        <div className='lm-brand-header'>
          {brand.picUrl && <img src={brand.picUrl} alt={brand.name} />}
          <div>
            <h1 className='h4 mb-1'>{brand.name}</h1>
            {brand.desc && <p className='text-muted mb-0'>{brand.desc}</p>}
          </div>
        </div>
      )}

      {goods.length === 0 ? (
        <p className='text-muted text-center my-5'>No products listed for this brand yet.</p>
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

export default BrandDetail;
