import React, { useState } from 'react';
import { Offcanvas } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { useAppSelector } from 'app/config/store';
import { useTranslation } from 'app/i18n';
import { useContentAvailability } from 'app/shared/util/useContentAvailability';
import { seasonLabel, seasonPath } from 'app/shared/util/season';
import { useSeason } from 'app/shared/util/useSeason';

// Categories come back as DDD aggregates (categoryId:{id} / categoryName /
// iconUrl) but some endpoints use the flat id/name shape — read whichever.
const catId = (c: any): number | undefined => c?.id ?? c?.categoryId?.id;
const catName = (c: any): string | undefined => c?.name ?? c?.categoryName;

interface Props {
  show: boolean;
  onHide: () => void;
}

/**
 * Amazon-style "☰ All" slide-in drawer: every L1 category (expandable to its
 * L2 children → /category/:id), plus Trending and Help shortcuts. Fed by the
 * catalog data already in Redux (Layout dispatches the fetch); every link
 * closes the drawer.
 */
const CategoryDrawer: React.FC<Props> = ({ show, onHide }) => {
  const { dataCategoryIndex, dataCatalogAll } = useAppSelector(state => state.category.data);
  const isAuthenticated = useAppSelector(state => state.customerAuth.data.isAuthenticated);
  const nickName = useAppSelector(state => state.customerAuth.data.userInfo?.nickName);
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const { t } = useTranslation();
  // Wave 26: same rule as the header/footer — a section is listed only while it
  // has something behind it (see shared/util/contentAvailability.ts).
  const hasBrands = useContentAvailability('brands');
  const hasTopics = useContentAvailability('topics');
  // Wave 27: the season collection, named by whoever activated it.
  const season = useSeason();

  // Prefer /catalog/all (carries every L1 category AND its subcategories);
  // fall back to /catalog/index's flat list.
  const categories = (dataCatalogAll?.categoryList?.length ? dataCatalogAll.categoryList : dataCategoryIndex?.categoryList) ?? [];
  const subTree = (dataCatalogAll?.allList ?? {}) as Record<string, any[]>;

  return (
    <Offcanvas show={show} onHide={onHide} placement='start' className='lm-drawer'>
      <Offcanvas.Header closeButton closeVariant='white' className='lm-drawer__head'>
        <Offcanvas.Title>
          <i className='bi bi-person-circle me-2' />
          {isAuthenticated ? t('header.hello', { name: nickName || t('header.shopper') }) : t('header.helloSignIn')}
        </Offcanvas.Title>
      </Offcanvas.Header>
      <Offcanvas.Body className='p-0'>
        <div className='lm-drawer__section-title'>{t('drawer.shopByCategory')}</div>
        {categories.map(category => {
          const cid = catId(category);
          const subs = subTree[String(cid)] ?? [];
          const expanded = expandedId === cid;
          return (
            <div key={cid} className='lm-drawer__group'>
              <div className='lm-drawer__row'>
                <Link to={`/category/${cid}`} className='lm-drawer__link' onClick={onHide}>
                  {catName(category)}
                </Link>
                {subs.length > 0 && (
                  <button
                    type='button'
                    className='lm-drawer__expand'
                    aria-expanded={expanded}
                    aria-label={t(expanded ? 'drawer.collapse' : 'drawer.expand', { name: catName(category) })}
                    onClick={() => setExpandedId(expanded ? null : (cid ?? null))}
                  >
                    <i className={`bi bi-chevron-${expanded ? 'up' : 'down'}`} />
                  </button>
                )}
              </div>
              {expanded && (
                <div className='lm-drawer__subs'>
                  {subs.map(sub => (
                    <Link key={catId(sub)} to={`/category/${catId(sub)}`} className='lm-drawer__sublink' onClick={onHide}>
                      {catName(sub)}
                    </Link>
                  ))}
                </div>
              )}
            </div>
          );
        })}

        <div className='lm-drawer__section-title'>{t('drawer.trending')}</div>
        {/* This said "Today's Deals" while pointing at /hot — the header strip
            uses that exact label for /deals, so the same words took a shopper to
            two different pages. /hot is the best-seller ranking. */}
        <Link to='/hot' className='lm-drawer__link d-block' onClick={onHide}>
          {t('drawer.bestSellers')}
        </Link>
        {season && seasonLabel(season) && (
          <Link to={seasonPath(season)} className='lm-drawer__link d-block' onClick={onHide}>
            {seasonLabel(season)}
          </Link>
        )}
        <Link to='/new' className='lm-drawer__link d-block' onClick={onHide}>
          {t('drawer.newArrivals')}
        </Link>
        {hasBrands && (
          <Link to='/brands' className='lm-drawer__link d-block' onClick={onHide}>
            {t('drawer.brands')}
          </Link>
        )}
        {hasTopics && (
          <Link to='/topics' className='lm-drawer__link d-block' onClick={onHide}>
            {t('drawer.topicsGuides')}
          </Link>
        )}

        <div className='lm-drawer__section-title'>{t('drawer.helpSettings')}</div>
        <Link to='/user' className='lm-drawer__link d-block' onClick={onHide}>
          {t('drawer.yourAccount')}
        </Link>
        <Link to='/orders' className='lm-drawer__link d-block' onClick={onHide}>
          {t('drawer.yourOrders')}
        </Link>
        <Link to='/service' className='lm-drawer__link d-block' onClick={onHide}>
          {t('drawer.customerService')}
        </Link>
        {!isAuthenticated && (
          <Link to='/login' className='lm-drawer__link d-block' onClick={onHide}>
            {t('drawer.signIn')}
          </Link>
        )}
      </Offcanvas.Body>
    </Offcanvas>
  );
};

export default CategoryDrawer;
