import { useTranslation } from 'app/i18n';
import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { BrandWithGoods, loadBrandsWithGoods } from 'app/shared/util/contentAvailability';
import { secureImageUrl } from 'app/shared/util/imageUrl';
import 'app/shared/scss/content.scss';

/**
 * Brand directory, modelled on litemall-vue `items/brand-list`. Sourced from
 * `/srv/brand/list` (live on goods-management).
 *
 * The seed carries ~49 brands and none of them has on-sale goods behind it
 * today, so listing every brand would dead-end at "No products" on every tile.
 * The populated-only rule — and the Wave-25 curation gate that hides raw
 * supplier legal names — live in `shared/util/contentAvailability.ts`, shared
 * with the nav entries that point here, so the page and the header/footer/drawer
 * can never disagree about whether this section has anything to show.
 */
const BrandList: React.FC = () => {
  const { t } = useTranslation('content');
  const [brands, setBrands] = useState<BrandWithGoods[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    loadBrandsWithGoods()
      .then(list => {
        if (!cancelled) setBrands(list);
      })
      .catch(() => {
        if (!cancelled) setBrands([]);
      })
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className='container my-4'>
      <h1 className='h4 mb-3'>{t('brand.title')}</h1>
      {loading ? (
        <div className='text-center my-5'>
          <Spinner animation='border' />
        </div>
      ) : brands.length === 0 ? (
        <p className='text-muted text-center my-5'>{t('brand.none')}</p>
      ) : (
        <div className='lm-brand-grid'>
          {brands.map(b => (
            <Link key={b.id} to={`/brand/${b.id}`} className='lm-brand-tile'>
              {secureImageUrl(b.picUrl) ? (
                <img src={secureImageUrl(b.picUrl) as string} alt={b.name} />
              ) : (
                <span className='lm-brand-tile__ph' aria-hidden='true' />
              )}
              <div className='lm-brand-tile__name'>{b.name}</div>
              <span className={`lm-brand-badge lm-brand-badge--${b.kind === 1 ? 'store' : 'brand'}`}>
                {b.kind === 1 ? 'Store' : 'Brand'}
              </span>
              <div className='lm-brand-tile__count'>
                {b.goodsCount} {b.goodsCount === 1 ? 'product' : 'products'}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};

export default BrandList;
