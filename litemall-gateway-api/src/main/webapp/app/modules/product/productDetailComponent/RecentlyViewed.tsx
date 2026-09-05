import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { userApi } from 'app/shared/api';
import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { money } from 'app/shared/util/money';
import { productPath } from 'app/shared/util/slug';
import { useTranslation } from 'app/i18n';

/**
 * "Recently viewed" card row under the related products (Amazon's
 * browsing-history carousel). Signed-in only — the PDP already records every
 * visit via `/srv/footprint/record`, so `/srv/footprint/list` is the ready
 * data source. The current product is excluded; anonymous visitors, an empty
 * history, or a failed fetch all render nothing.
 */
interface Props {
  currentGoodsId?: number | string;
}

interface FootItem {
  id?: number;
  goodsId?: number | string;
  name?: string;
  goodsName?: string;
  picUrl?: string;
  retailPrice?: unknown;
}

const RecentlyViewed: React.FC<Props> = ({ currentGoodsId }) => {
  const { t } = useTranslation('product');
  const [items, setItems] = useState<FootItem[]>([]);

  useEffect(() => {
    if (!sessionStorage.getItem('customerToken')) return;
    let cancelled = false;
    userApi
      .footprintList({ page: 1, limit: 12 })
      .then((res: any) => {
        if (!cancelled) setItems(((res?.list ?? res ?? []) as FootItem[]).filter(it => it.goodsId != null));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [currentGoodsId]);

  const rows = items.filter(it => String(it.goodsId) !== String(currentGoodsId)).slice(0, 8);
  if (!rows.length) return null;

  return (
    <section className='lm-pdp__recent'>
      <h3 className='lm-pdp__recenttitle'>{t('recent')}</h3>
      <div className='lm-pdp__recentrow'>
        {rows.map(it => (
          <Link key={`${it.goodsId}-${it.id ?? ''}`} to={productPath(it.goodsId!, it.name ?? it.goodsName)} className='lm-pdp__recentcard'>
            <img src={it.picUrl} alt='' loading='lazy' onError={e => (e.currentTarget.style.visibility = 'hidden')} />
            <span className='lm-pdp__recentname'>{it.name ?? it.goodsName}</span>
            <span className='lm-pdp__recentprice'>{money(priceNum(it.retailPrice))}</span>
          </Link>
        ))}
      </div>
    </section>
  );
};

export default RecentlyViewed;
