import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useTranslation } from 'app/i18n';

/**
 * Amazon-style category trail above the title: `Home › L1 › L2 › leaf`, each
 * category linking its `/category/:id` landing. The ancestor chain comes from
 * goods-management's `GET /srv/search/category/{id}?size=1` (`breadcrumb`
 * node list — the same Wave-9 seam CategoryTree uses on the landings; the
 * detail payload's `categoryIds` carry only the leaf id, and the catalog-tree
 * endpoints can't walk upward). Strictly decorative: any failure or empty
 * chain renders nothing.
 */
interface Props {
  categoryIds?: Array<number | string>;
}

type Node = { id: number; name: string };

const Breadcrumb: React.FC<Props> = ({ categoryIds }) => {
  const { t } = useTranslation('product');
  const leafId = (categoryIds ?? []).map(Number).filter(Number.isFinite).pop();
  const [crumbs, setCrumbs] = useState<Node[]>([]);

  useEffect(() => {
    setCrumbs([]);
    if (leafId == null) return;
    let cancelled = false;
    // size=1: only the breadcrumb chain is needed, not the category's hits.
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search/category/${leafId}?size=1`)
      .then(res => {
        const chain = (res.data?.data?.breadcrumb ?? []) as Node[];
        if (!cancelled) setCrumbs(chain.filter(c => c?.id != null && c?.name));
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [leafId]);

  if (!crumbs.length) return null;

  return (
    <nav className='lm-pdp__crumbs' aria-label={t('breadcrumb.aria')}>
      <Link to='/'>{t('breadcrumb.home')}</Link>
      {crumbs.map(c => (
        <React.Fragment key={c.id}>
          <span className='lm-pdp__crumbsep' aria-hidden='true'>
            ›
          </span>
          <Link to={`/category/${c.id}`}>{c.name}</Link>
        </React.Fragment>
      ))}
    </nav>
  );
};

export default Breadcrumb;
