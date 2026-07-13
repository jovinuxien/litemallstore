import React, { useEffect, useState } from 'react';
import { Carousel } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { contentApi, IPageView } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import PageRenderer from '../page/PageRenderer';
import ProductCard, { goodId } from '../../components/userComponents/card/ProductCard';
import InfiniteProductGrid from '../../components/userComponents/card/InfiniteProductGrid';
import { getCatalogAllData, getCatalogIndexData } from '../Category/categorySlice';
import { getProductList } from '../product/productSlice';
import { getHomeData } from './homeSlice';
import 'app/components/userComponents/card/product-card.scss';
import './storefront-home.scss';

// Categories come back as DDD aggregates (categoryId:{id} / categoryName /
// iconUrl) but some endpoints use the flat id/name shape — read whichever.
const catId = (c: any): number | undefined => c?.id ?? c?.categoryId?.id;
const catName = (c: any): string | undefined => c?.name ?? c?.categoryName;
const catIcon = (c: any): string | undefined => c?.iconUrl ?? c?.picUrl;

// Small section wrapper with a title + optional "see more" link.
const Section: React.FC<{ title: string; moreTo?: string; children: React.ReactNode }> = ({ title, moreTo, children }) => (
  <section className="lm-section">
    <div className="lm-section__head">
      <h2 className="lm-section__title">{title}</h2>
      {moreTo && (
        <Link to={moreTo} className="lm-section__more">
          See more ›
        </Link>
      )}
    </div>
    {children}
  </section>
);

const HomeView: React.FC = () => {
  const dispatch = useAppDispatch();
  const entities = useAppSelector(state => state.home.homeData);
  const { list } = useAppSelector(state => state.product.data);
  const { dataCategoryIndex, dataCatalogAll } = useAppSelector(state => state.category.data);
  // Mobile: the category tree collapses behind an "All categories" toggle; desktop keeps it open.
  const [menuOpen, setMenuOpen] = useState(false);
  // DIY home (goods-management Wave 4, spec-page-palette-v1.md): when an admin
  // has activated a home page, render it instead of the legacy home. errno 642
  // (none active), 404/501 (backend not shipped) or any failure ⇒ legacy home,
  // no error UI — the legacy layout stays the default and renders immediately.
  const [diyPage, setDiyPage] = useState<IPageView | null>(null);

  useEffect(() => {
    let cancelled = false;
    contentApi
      .pageHome()
      .then(p => {
        if (!cancelled && p && Array.isArray(p.components)) setDiyPage(p);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getProductList());
    dispatch(getCatalogIndexData());
    dispatch(getCatalogAllData());
  }, [dispatch]);

  if (diyPage) {
    return <PageRenderer page={diyPage} />;
  }

  const banners = entities?.banner ?? [];
  const channels = entities?.channel ?? [];
  const coupons = entities?.couponList ?? [];
  const hotGoods = (entities?.hotGoodsList ?? []) as IGood[];
  const newGoods = (entities?.newGoodsList ?? []) as IGood[];
  const brands = (entities?.brandList ?? []) as Array<{ id?: number; name?: string; picUrl?: string }>;
  const topics = (entities?.topicList ?? []) as Array<{ id?: number; title?: string; subtitle?: string; picUrl?: string }>;
  const floors = entities?.floorGoodsList ?? [];
  // Prefer the /catalog/all payload (carries every L1 category AND its
  // subcategories for the flyout); fall back to /catalog/index's flat list.
  const menuCategories = (dataCatalogAll?.categoryList?.length ? dataCatalogAll.categoryList : dataCategoryIndex?.categoryList) ?? [];
  const subTree = dataCatalogAll?.allList ?? {};
  const deals = (list ?? []) as IGood[];

  return (
    <div className="lm-home">
      <div className="lm-container">
        {/* Channel quick-links */}
        {channels.length > 0 && (
          <nav className="lm-channels">
            {channels.map(channel => {
              // channel items are categories: categoryId:{id} / categoryName / iconUrl.
              const chid = catId(channel);
              const chname = catName(channel);
              const subs = (subTree[String(chid)] ?? subTree[chid as any] ?? []) as any[];
              return (
                <div key={chid} className="lm-channel-item">
                  <Link to={`/category/${chid}`} className="lm-channel">
                    <img src={catIcon(channel)} alt={chname} loading="lazy" />
                    <span>{chname}</span>
                  </Link>
                  {subs.length > 0 && (
                    <div className="lm-channel-dropdown">
                      <div className="lm-channel-dropdown__inner">
                        {subs.map(sub => (
                          <Link key={catId(sub)} to={`/category/${catId(sub)}`} className="lm-channel-dropdown__item">
                            {catIcon(sub) && <img src={catIcon(sub)} alt="" loading="lazy" />}
                            <span>{catName(sub)}</span>
                          </Link>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </nav>
        )}

        {/* Hero: category menu + banner carousel + welcome aside */}
        <div className="lm-hero">
          <button
            type="button"
            className="lm-hero__menu-toggle"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen(open => !open)}
          >
            <span className="lm-hero__menu-toggle-icon">☰</span> All categories
            <span className="lm-hero__menu-toggle-caret">{menuOpen ? '▴' : '▾'}</span>
          </button>
          <aside className={`lm-hero__menu${menuOpen ? ' is-open' : ''}`}>
            {/* categoryList arrives most-promising-first (server orders by on-sale goods count) */}
            {menuCategories.slice(0, 10).map(category => {
              const cid = catId(category);
              // Subcategories keyed by string id (JSON object keys are strings).
              const subs = (subTree[String(cid)] ?? subTree[cid as any] ?? []) as any[];
              return (
                <div key={cid} className="lm-menu-row">
                  <Link to={`/category/${cid}`} className="lm-menu-row__link">
                    {catIcon(category) && <img src={catIcon(category)} alt="" loading="lazy" />}
                    <span>{catName(category)}</span>
                    {subs.length > 0 && <span className="lm-menu-row__caret">›</span>}
                  </Link>
                  {subs.length > 0 && (
                    <div className="lm-flyout">
                      <div className="lm-flyout__inner">
                        {subs.map(sub => (
                          <Link key={catId(sub)} to={`/category/${catId(sub)}`} className="lm-flyout__item">
                            {catIcon(sub) && <img src={catIcon(sub)} alt="" loading="lazy" />}
                            <span>{catName(sub)}</span>
                          </Link>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </aside>

          <div className="lm-hero__banner">
            {banners.length > 0 ? (
              <Carousel fade>
                {banners.map(banner => (
                  <Carousel.Item key={banner.id}>
                    <a href={banner.link || '#'}>
                      <img src={banner.url} alt={banner.name} />
                    </a>
                    {banner.name && (
                      <Carousel.Caption>
                        <h3>{banner.name}</h3>
                      </Carousel.Caption>
                    )}
                  </Carousel.Item>
                ))}
              </Carousel>
            ) : (
              <div style={{ height: 340 }} />
            )}
          </div>

          <div className="lm-hero__aside">
            <div className="lm-welcome">
              <h4>Welcome to litemall</h4>
              <p>Sign in for member prices, coupons and faster checkout.</p>
              {/* <Link to="/login" className="lm-welcome__btn">
                Sign in / Register
              </Link> */}
            </div>
            {coupons.slice(0, 1).map(coupon => (
              <div key={coupon.id} className="lm-coupon">
                <div className="lm-coupon__amount">${coupon.discount}</div>
                <div>
                  <div className="lm-coupon__name">{coupon.name}</div>
                  {coupon.min != null && <div className="lm-coupon__min">Spend ${coupon.min}</div>}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Coupons strip */}
        {coupons.length > 0 && (
          <Section title="Coupons & deals">
            <div className="lm-coupons">
              {coupons.map(coupon => (
                <div key={coupon.id} className="lm-coupon">
                  <div className="lm-coupon__amount">${coupon.discount}</div>
                  <div>
                    <div className="lm-coupon__name">{coupon.name}</div>
                    {coupon.min != null && <div className="lm-coupon__min">Spend ${coupon.min}</div>}
                  </div>
                </div>
              ))}
            </div>
          </Section>
        )}

        {/* SuperDeals — horizontal rail of hot goods */}
        {hotGoods.length > 0 && (
          <Section title="SuperDeals">
            <div className="lm-rail">
              {hotGoods.map((product, i) => (
                <ProductCard key={`hot-${goodId(product) ?? i}`} product={product} />
              ))}
            </div>
          </Section>
        )}

        {/* New arrivals */}
        {newGoods.length > 0 && (
          <Section title="New arrivals">
            <div className="lm-grid">
              {newGoods.slice(0, 12).map((product, i) => (
                <ProductCard key={`new-${goodId(product) ?? i}`} product={product} />
              ))}
            </div>
          </Section>
        )}

        {/* Brand zone */}
        {brands.length > 0 && (
          <Section title="Brands">
            <div className="lm-brands">
              {brands.map(brand => (
                <Link key={brand.id} to={`/brand/${brand.id}`} className="lm-brand">
                  <img src={brand.picUrl} alt={brand.name} loading="lazy" />
                  <span>{brand.name}</span>
                </Link>
              ))}
            </div>
          </Section>
        )}

        {/* Topics */}
        {topics.length > 0 && (
          <Section title="Discover">
            <div className="lm-topics">
              {topics.slice(0, 3).map(topic => (
                <Link key={topic.id} to={`/topic/${topic.id}`} className="lm-topic">
                  <img src={topic.picUrl} alt={topic.title} loading="lazy" />
                  <div className="lm-topic__overlay">
                    <h3 className="lm-topic__title">{topic.title}</h3>
                    {topic.subtitle && <p className="lm-topic__subtitle">{topic.subtitle}</p>}
                  </div>
                </Link>
              ))}
            </div>
          </Section>
        )}

        {/* Category floors */}
        {floors.map(floor => {
          const goods = (floor?.goodsList ?? []) as IGood[];
          if (goods.length === 0) return null;
          // Backend sends the floor title as `name`; the TS model calls it
          // `nameCategory`. Read whichever is present.
          const title = (floor as { name?: string }).name ?? floor.nameCategory ?? 'Category';
          return (
            <Section key={`floor-${floor.id}`} title={title} moreTo={floor.id ? `/category/${floor.id}` : undefined}>
              <div className="lm-floor">
                <div className="lm-grid">
                  {goods.slice(0, 12).map((product, i) => (
                    <ProductCard key={`floor-${floor.id}-${goodId(product) ?? i}`} product={product} />
                  ))}
                </div>
              </div>
            </Section>
          );
        })}

        {/* More to love — infinite feed */}
        {/* {deals.length > 0 && (
          <Section title="More to love">
            <InfiniteProductGrid items={deals} keyPrefix="deal" />
          </Section>
        )} */}
      </div>
    </div>
  );
};

export default HomeView;
