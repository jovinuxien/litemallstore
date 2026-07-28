import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { baseAxios, contentApi, IArticle, IPageComponent, IPageView, SRV, unwrap } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import ProductCard, { goodId } from 'app/components/userComponents/card/ProductCard';
import { productPath } from 'app/shared/util/slug';
import 'app/components/userComponents/card/product-card.scss';
import 'app/modules/home/storefront-home.scss';

/**
 * Palette v1 renderer (spec-page-palette-v1.md — NORMATIVE). Renders an
 * ordered `components[]` array through a per-type registry, applying degrade
 * rule R1 (§3): a component whose data fetch fails or comes back empty is
 * SKIPPED silently — one dead component never breaks the page. Static
 * components (banner, image-row, rich-text) always render.
 *
 * Envelope quirks (per spec §2):
 * - `POST /srv/goods/batch` (byIds) returns a RAW `{goodsId: aggregate}` map,
 *   NO envelope; missing/off-sale ids are absent; configured order preserved.
 * - `/srv/promotion/coupon/available` and `/srv/promotion/seckill/active`
 *   return BARE arrays and take no limit param — the renderer slices.
 * - `/srv/article/list` is the standard `{errno, data:{list,total}}` shape.
 */

/** Internal SPA routes get <Link>, anything else a plain anchor. */
const MaybeLink: React.FC<{ link?: string; className?: string; children: React.ReactNode }> = ({ link, className, children }) => {
  if (!link) return <div className={className}>{children}</div>;
  if (link.startsWith('/')) {
    return (
      <Link to={link} className={className}>
        {children}
      </Link>
    );
  }
  return (
    <a href={link} className={className}>
      {children}
    </a>
  );
};

const Section: React.FC<{ title?: string; children: React.ReactNode }> = ({ title, children }) => (
  <section className='lm-section'>
    {title && (
      <div className='lm-section__head'>
        <h2 className='lm-section__title'>{title}</h2>
      </div>
    )}
    {children}
  </section>
);

// --- static components -------------------------------------------------------

const BannerC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const image = config.image as string | undefined;
  if (!image) return null;
  return (
    <div className='lm-page-banner my-3'>
      <MaybeLink link={config.link as string | undefined}>
        <div style={{ position: 'relative' }}>
          <img src={image} alt={(config.title as string) ?? ''} style={{ width: '100%', borderRadius: 8, display: 'block' }} />
          {!!config.title && (
            <div
              style={{
                position: 'absolute',
                left: 16,
                bottom: 12,
                color: '#fff',
                textShadow: '0 1px 3px rgba(0,0,0,.6)',
                fontSize: '1.25rem',
                fontWeight: 600,
              }}
            >
              {config.title as string}
            </div>
          )}
        </div>
      </MaybeLink>
    </div>
  );
};

const ImageRowC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const images = (config.images as Array<{ image?: string; link?: string }> | undefined) ?? [];
  const usable = images.filter(i => i?.image);
  if (usable.length === 0) return null;
  return (
    <div className='d-flex gap-2 my-3' style={{ overflowX: 'auto' }}>
      {usable.map((img, i) => (
        <MaybeLink key={i} link={img.link} className='flex-fill'>
          <img src={img.image} alt='' style={{ width: '100%', borderRadius: 8, display: 'block' }} loading='lazy' />
        </MaybeLink>
      ))}
    </div>
  );
};

const RichTextC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const html = config.html as string | undefined;
  if (!html) return null;
  // Sanitized SERVER-side on save (jsoup custom Safelist, clean-and-store) —
  // safe to inject per spec §2.7.
  return <div className='lm-page-richtext my-3' dangerouslySetInnerHTML={{ __html: html }} />;
};

// --- dynamic components (R1: error/empty ⇒ render nothing) -------------------

const GoodsListC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [goods, setGoods] = useState<IGood[]>([]);

  useEffect(() => {
    let cancelled = false;
    const mode = config.mode as string;
    const limit = Math.min(Math.max(Number(config.limit) || 8, 1), 24);
    const load = async (): Promise<IGood[]> => {
      if (mode === 'byIds') {
        const ids = (config.goodsIds as number[] | undefined) ?? [];
        if (ids.length === 0) return [];
        // RAW map {goodsId: aggregate} — no envelope; unwrap passes it through.
        const map = await unwrap<Record<string, IGood>>(baseAxios.post(`${SRV}/goods/batch`, ids));
        if (!map || typeof map !== 'object') return [];
        // Preserve the configured order; missing/off-sale ids are absent.
        return ids.map(id => map[String(id)]).filter(Boolean) as IGood[];
      }
      if (mode === 'deals') {
        // Search envelope ({goodsList}, NOT {list}) — spec §2.3 v1.1. Scored browse:
        // deepest-discount × most-popular deals first.
        const res =
          (await unwrap<{ goodsList?: IGood[] }>(baseAxios.get(`${SRV}/search`, { params: { deal_flag: 1, size: limit, page: 1 } }))) ??
          {};
        return res.goodsList ?? [];
      }
      const params =
        mode === 'byCategory'
          ? { categoryId: config.categoryId as number, limit, page: 1 }
          : mode === 'hot'
            ? { isHot: true, limit, page: 1 }
            : mode === 'new'
              ? { isNew: true, limit, page: 1 }
              : null;
      if (!params) return [];
      const res = (await unwrap<{ list?: IGood[] }>(baseAxios.get(`${SRV}/goods/list`, { params }))) ?? {};
      return res.list ?? [];
    };
    load()
      .then(list => {
        if (!cancelled) setGoods(list);
      })
      .catch(() => {
        // R1: fetch failure ⇒ component skipped.
        if (!cancelled) setGoods([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (goods.length === 0) return null;
  return (
    <Section title={config.title as string | undefined}>
      <div className='lm-grid'>
        {goods.map((g, i) => (
          <ProductCard key={`pg-${goodId(g) ?? i}`} product={g} />
        ))}
      </div>
    </Section>
  );
};

interface ICouponAvailable {
  couponId?: number;
  name?: string;
  description?: string;
  tag?: string;
  discount?: number;
  min?: number;
}

const CouponStripC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [coupons, setCoupons] = useState<ICouponAvailable[]>([]);

  useEffect(() => {
    let cancelled = false;
    const limit = Math.min(Math.max(Number(config.limit) || 3, 1), 10);
    // BARE array, no limit param — the renderer slices (spec §2.4).
    unwrap<ICouponAvailable[]>(baseAxios.get(`${SRV}/promotion/coupon/available`))
      .then(arr => {
        if (!cancelled) setCoupons(Array.isArray(arr) ? arr.slice(0, limit) : []);
      })
      .catch(() => {
        if (!cancelled) setCoupons([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (coupons.length === 0) return null;
  return (
    <Section title={(config.title as string) ?? 'Coupons'}>
      <div className='lm-coupons'>
        {coupons.map((c, i) => (
          <Link key={c.couponId ?? i} to='/user/coupons' className='lm-coupon text-decoration-none'>
            <div className='lm-coupon__amount'>${c.discount}</div>
            <div>
              <div className='lm-coupon__name'>{c.name}</div>
              {c.min != null && <div className='lm-coupon__min'>Spend ${c.min}</div>}
            </div>
          </Link>
        ))}
      </div>
    </Section>
  );
};

interface ISeckillActive {
  seckillId?: number;
  goodsId?: number;
  goodsName?: string;
  picUrl?: string;
  price?: number;
  stock?: number;
  sales?: number;
}

const SeckillStripC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [items, setItems] = useState<ISeckillActive[]>([]);

  useEffect(() => {
    let cancelled = false;
    const limit = Math.min(Math.max(Number(config.limit) || 3, 1), 10);
    // BARE array, no limit param — renderer slices (spec §2.5).
    unwrap<ISeckillActive[]>(baseAxios.get(`${SRV}/promotion/seckill/active`))
      .then(arr => {
        if (!cancelled) setItems(Array.isArray(arr) ? arr.slice(0, limit) : []);
      })
      .catch(() => {
        if (!cancelled) setItems([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (items.length === 0) return null;
  return (
    <Section title={(config.title as string) ?? 'Flash sale'}>
      <div className='lm-rail'>
        {items.map((s, i) => {
          const body = (
            <div className='card p-2 h-100' style={{ minWidth: 180 }}>
              {s.picUrl && <img src={s.picUrl} alt='' style={{ width: '100%', borderRadius: 6 }} loading='lazy' />}
              <div className='small text-truncate mt-1'>{s.goodsName}</div>
              <div className='fw-bold text-danger'>${s.price}</div>
              {s.sales != null && <div className='small text-muted'>{s.sales} sold</div>}
            </div>
          );
          return s.goodsId ? (
            <Link key={s.seckillId ?? i} to={productPath(s.goodsId, s.goodsName)} className='text-decoration-none text-reset'>
              {body}
            </Link>
          ) : (
            <div key={s.seckillId ?? i}>{body}</div>
          );
        })}
      </div>
    </Section>
  );
};

const ArticleStripC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [articles, setArticles] = useState<IArticle[]>([]);

  useEffect(() => {
    let cancelled = false;
    const limit = Math.min(Math.max(Number(config.limit) || 3, 1), 10);
    // This endpoint DOES page server-side (spec §2.6).
    contentApi
      .articleList(1, limit, undefined, (config.hotOnly as boolean) || undefined)
      .then(res => {
        if (!cancelled) setArticles(res?.list ?? []);
      })
      .catch(() => {
        if (!cancelled) setArticles([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (articles.length === 0) return null;
  return (
    <Section title={(config.title as string) ?? 'Articles'}>
      <div className='row g-3'>
        {articles.map(a => (
          <div key={a.id} className='col-md-4'>
            <Link to={`/article/${a.id}`} className='card h-100 p-3 text-decoration-none text-reset'>
              {a.picUrl && <img src={a.picUrl} alt='' className='mb-2' style={{ width: '100%', borderRadius: 6 }} loading='lazy' />}
              <div className='fw-semibold'>{a.title}</div>
              {a.summary && <div className='small text-muted text-truncate'>{a.summary}</div>}
            </Link>
          </div>
        ))}
      </div>
    </Section>
  );
};

// --- registry -----------------------------------------------------------------

const REGISTRY: Record<string, React.FC<{ config: Record<string, unknown> }>> = {
  banner: BannerC,
  'image-row': ImageRowC,
  'goods-list': GoodsListC,
  'coupon-strip': CouponStripC,
  'seckill-strip': SeckillStripC,
  'article-strip': ArticleStripC,
  'rich-text': RichTextC,
};

const PageRenderer: React.FC<{ page: IPageView }> = ({ page }) => (
  <div className='lm-home'>
    <div className='lm-container'>
      {(page.components ?? []).map((c: IPageComponent, i: number) => {
        const C = REGISTRY[c.type];
        // Unknown type: server-side validation should prevent this; R1 says skip.
        if (!C) return null;
        return <C key={c.key ?? `${c.type}-${i}`} config={c.config ?? {}} />;
      })}
    </div>
  </div>
);

export default PageRenderer;
