import React, { useEffect, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { catalogApi, contentApi, IBrand } from 'app/shared/api';
import { attributionOf } from 'app/shared/util/attribution';
import 'app/shared/scss/content.scss';

/**
 * Brand directory, modelled on litemall-vue `items/brand-list`. Sourced from
 * `/srv/brand/list` (live on goods-management).
 *
 * The seed carries ~49 brands but most native brand rows have no goods pointing
 * at them today (the catalog is dominated by CJ imports), so listing every brand
 * dead-ends at "No products" on most tiles. Until goods-management returns a
 * per-brand goods count (see docs/handoff-brand-goods-count.md), we probe each
 * brand's on-sale count client-side (`/srv/goods/list?brandId=&limit=1`) and show
 * only the populated ones, most products first. Cached for the session so
 * revisiting `/brands` is instant.
 */
type BrandWithCount = IBrand & { goodsCount: number };

// Session cache: populated the first time /brands is opened.
let brandsCache: BrandWithCount[] | null = null;

const BrandList: React.FC = () => {
  const [brands, setBrands] = useState<BrandWithCount[]>(brandsCache ?? []);
  const [loading, setLoading] = useState(brandsCache === null);

  useEffect(() => {
    if (brandsCache !== null) return;
    let cancelled = false;

    const countFor = async (b: IBrand): Promise<number> => {
      // Trust a backend-supplied count if it ever lands (handoff), else probe.
      const supplied = (b as { goodsCount?: number }).goodsCount;
      if (typeof supplied === 'number') return supplied;
      if (b.id == null) return 0;
      try {
        const res: any = await catalogApi.goodsList({ brandId: b.id, page: 1, limit: 1 });
        return Number(res?.total ?? res?.list?.length ?? 0) || 0;
      } catch {
        return 0;
      }
    };

    contentApi
      .brandList({ page: 1, limit: 100 })
      .then(async res => {
        // Wave-25 curation gate: never list a row that isn't display-enabled
        // (provider-captured supplier rows carry raw legal names until an
        // admin renames + enables them).
        const all = (res?.list ?? []).filter(b => attributionOf(b) !== null);
        const counts = await Promise.all(all.map(countFor));
        const populated = all
          .map((b, i) => ({ ...b, goodsCount: counts[i] }))
          .filter(b => b.goodsCount > 0)
          .sort((a, b) => b.goodsCount - a.goodsCount);
        brandsCache = populated;
        if (!cancelled) setBrands(populated);
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
      <h1 className='h4 mb-3'>Brands &amp; stores</h1>
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
