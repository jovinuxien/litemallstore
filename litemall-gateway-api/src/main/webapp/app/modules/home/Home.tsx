import React, { useEffect, useState } from 'react';
import { Carousel } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { BASE_URL_CONTEXT } from 'app/config/api';
import { baseAxios } from 'app/config/axiosinstance';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { contentApi, IPageView } from 'app/shared/api';
import { IBanner } from 'app/shared/model/home.models';
import { couponValueShort } from 'app/shared/util/couponFormat';
import { EURO } from 'app/shared/util/money';
import { IGood } from 'app/shared/model/product/product.model';
import PageRenderer from '../page/PageRenderer';
import ProductCard, { goodId } from '../../components/userComponents/card/ProductCard';
import InfiniteProductGrid from '../../components/userComponents/card/InfiniteProductGrid';
import { getCatalogAllData, getCatalogIndexData } from '../Category/categorySlice';
import { secureImageUrl } from 'app/shared/util/imageUrl';
import { useContentAvailability } from 'app/shared/util/useContentAvailability';
import { getProductList } from '../product/productSlice';
import { getHomeData } from './homeSlice';
import 'app/components/userComponents/card/product-card.scss';
import './storefront-home.scss';

// Categories come back as DDD aggregates (categoryId:{id} / categoryName /
// iconUrl) but some endpoints use the flat id/name shape — read whichever.
const catId = (c: any): number | undefined => c?.id ?? c?.categoryId?.id;
const catName = (c: any): string | undefined => c?.name ?? c?.categoryName;
const catIcon = (c: any): string | undefined => c?.iconUrl ?? c?.picUrl;
const catPic = (c: any): string | undefined => c?.picUrl ?? c?.iconUrl;

// Column count of the responsive .lm-grid (storefront-home.scss breakpoints),
// tracked live so the category tile nav always renders full rows that line up
// with the product grid.
const gridCols = (): number =>
  window.innerWidth >= 1200 ? 6 : window.innerWidth >= 992 ? 5 : window.innerWidth >= 768 ? 4 : window.innerWidth >= 576 ? 3 : 2;

const useGridColumns = (): number => {
  const [cols, setCols] = useState(gridCols);
  useEffect(() => {
    const onResize = () => setCols(gridCols());
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return cols;
};

// One hero-carousel slide. Banners follow the Wave-11 contract: `link` values
// starting with "/" are internal SPA routes (client-side navigation), absolute
// URLs keep a plain anchor, and a missing link means the slide isn't clickable.
// The hero images are square catalog crops, so the caption sits on a scrim.
const BannerSlide: React.FC<{ banner: IBanner; eager?: boolean }> = ({ banner, eager }) => {
  const body = (
    <>
      <img src={banner.url} alt={banner.name ?? ''} loading={eager ? 'eager' : 'lazy'} />
      {banner.name && (
        <div className="lm-banner__caption">
          <h3 className="lm-banner__title">{banner.name}</h3>
          {banner.content && <p className="lm-banner__subtitle">{banner.content}</p>}
          <span className="lm-banner__cta">Shop {banner.name} ›</span>
        </div>
      )}
    </>
  );
  const link = banner.link ?? '';
  if (link.startsWith('/')) {
    return (
      <Link to={link} className="lm-banner">
        {body}
      </Link>
    );
  }
  if (/^https?:\/\//i.test(link)) {
    return (
      <a href={link} className="lm-banner">
        {body}
      </a>
    );
  }
  return <div className="lm-banner">{body}</div>;
};

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
  // Wave 26: the home payload can still carry the legacy brand/topic seeds,
  // which have no goods behind them on the narrowed catalogue. Same shared
  // probe as the header/drawer/footer — one request per session, not per zone.
  const hasBrands = useContentAvailability('brands');
  const hasTopics = useContentAvailability('topics');
  // Mobile: the hero category tree collapses behind a toggle; desktop keeps it open.
  const [menuOpen, setMenuOpen] = useState(false);
  const cols = useGridColumns();
  // Summer Deals — CJ goods matching "summer" on the OCS relevance ranking.
  const [summerGoods, setSummerGoods] = useState<IGood[]>([]);
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

  useEffect(() => {
    let cancelled = false;
    baseAxios
      .get(`${BASE_URL_CONTEXT}/search`, { params: { q: 'summer', source: 'cj', page: 1, size: 8 } })
      .then(res => {
        const d = res.data?.data ?? res.data ?? {};
        if (!cancelled) setSummerGoods((d.goodsList ?? []) as IGood[]);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, []);

  if (diyPage) {
    return <PageRenderer page={diyPage} />;
  }

  const banners = entities?.banner ?? [];
  const coupons = entities?.couponList ?? [];
  const hotGoods = (entities?.hotGoodsList ?? []) as IGood[];
  const newGoods = (entities?.newGoodsList ?? []) as IGood[];
  const brands = (entities?.brandList ?? []) as Array<{ id?: number; name?: string; picUrl?: string }>;
  const topics = (entities?.topicList ?? []) as Array<{ id?: number; title?: string; subtitle?: string; picUrl?: string }>;
  const floors = entities?.floorGoodsList ?? [];
  // Prefer the /catalog/all payload (carries every L1 category AND its
  // subcategories for the flyout); fall back to /catalog/index's flat list.
  // categoryList arrives most-promising-first (server ranks by on-sale goods).
  const menuCategories = (dataCatalogAll?.categoryList?.length ? dataCatalogAll.categoryList : dataCategoryIndex?.categoryList) ?? [];
  const subTree = dataCatalogAll?.allList ?? {};
  const deals = (list ?? []) as IGood[];

  return (
    <div className="lm-home">
      <div className="lm-container">
        {/* Category picture-tile nav: the ranked category list as image tiles
            (name overlays on hover), always full rows — the column count
            mirrors the .lm-grid product-grid breakpoints. */}
        {menuCategories.length > 0 && (
          <nav className="lm-catnav" aria-label="Shop by category">
            {menuCategories.slice(0, cols * 2).map(category => {
              const cid = catId(category);
              const cname = catName(category);
              return (
                <Link key={cid} to={`/category/${cid}`} className="lm-catnav__tile" title={cname}>
                  {catPic(category) ? <img src={catPic(category)} alt={cname} loading="lazy" /> : <span className="lm-catnav__ph" />}
                  <span className="lm-catnav__name">{cname}</span>
                </Link>
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
                {banners.map((banner, i) => (
                  <Carousel.Item key={banner.id ?? `banner-${i}`}>
                    <BannerSlide banner={banner} eager={i === 0} />
                  </Carousel.Item>
                ))}
              </Carousel>
            ) : (
              <div className="lm-hero__banner-empty">
                <h3>Welcome to Trovemo</h3>
                <p>Fresh finds across every category, shipped to your door.</p>
                <Link to="/search" className="lm-hero__banner-empty-btn">
                  Browse all products ›
                </Link>
              </div>
            )}
          </div>

          <div className="lm-hero__aside">
            <div className="lm-welcome">
              <h4>Welcome to Trovemo</h4>
              <p>Sign in for member prices, coupons and faster checkout.</p>
              {/* <Link to="/login" className="lm-welcome__btn">
                Sign in / Register
              </Link> */}
            </div>
            {coupons.slice(0, 1).map(coupon => (
              <div key={coupon.id} className="lm-coupon">
                <div className="lm-coupon__amount">{couponValueShort(coupon)}</div>
                <div>
                  <div className="lm-coupon__name">{coupon.name}</div>
                  {coupon.min != null && <div className="lm-coupon__min">Spend {EURO}{coupon.min}</div>}
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
                  <div className="lm-coupon__amount">{couponValueShort(coupon)}</div>
                  <div>
                    <div className="lm-coupon__name">{coupon.name}</div>
                    {coupon.min != null && <div className="lm-coupon__min">Spend {EURO}{coupon.min}</div>}
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

        {/* Summer Deals — CJ goods matching "summer", OCS relevance-ranked */}
        {summerGoods.length > 0 && (
          <Section title="Summer Deals" moreTo="/summer">
            <div className="lm-rail">
              {summerGoods.map((product, i) => (
                <ProductCard key={`summer-${goodId(product) ?? i}`} product={product} />
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
        {hasBrands && brands.length > 0 && (
          <Section title="Brands">
            <div className="lm-brands">
              {brands.map(brand => (
                <Link key={brand.id} to={`/brand/${brand.id}`} className="lm-brand">
                  {secureImageUrl(brand.picUrl) && <img src={secureImageUrl(brand.picUrl) as string} alt={brand.name} loading="lazy" />}
                  <span>{brand.name}</span>
                </Link>
              ))}
            </div>
          </Section>
        )}

        {/* Topics */}
        {hasTopics && topics.length > 0 && (
          <Section title="Discover">
            <div className="lm-topics">
              {topics.slice(0, 3).map(topic => (
                <Link key={topic.id} to={`/topic/${topic.id}`} className="lm-topic">
                  {secureImageUrl(topic.picUrl) && <img src={secureImageUrl(topic.picUrl) as string} alt={topic.title} loading="lazy" />}
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
