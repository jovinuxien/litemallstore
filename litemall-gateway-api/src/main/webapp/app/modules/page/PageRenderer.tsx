import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';

import { baseAxios, contentApi, IArticle, ICombination, IPageComponent, IPageView, promotionApi, SRV, unwrap } from 'app/shared/api';
import { IGood } from 'app/shared/model/product/product.model';
import ProductCard, { goodId } from 'app/components/userComponents/card/ProductCard';
import { productPath } from 'app/shared/util/slug';
import 'app/components/userComponents/card/product-card.scss';
import 'app/modules/home/storefront-home.scss';

/**
 * Palette v1/v1.1 renderer (spec-page-palette-v1.md — NORMATIVE; Wave-20
 * CONTRACT adds `groupon-strip` and extends `coupon-strip` with
 * couponIds/headline/style — v1 configs must render byte-identically). Renders an
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
  /** Wave-18: 0/absent = flat dollars off; 1 = percent (discount holds the rate). */
  discountType?: number;
  discountCap?: number;
}

/** Config int[] fields arrive as unknown JSON — coerce to finite numbers only. */
const numberIds = (value: unknown): number[] =>
  Array.isArray(value) ? value.map(Number).filter(n => Number.isFinite(n)) : [];

const CouponStripC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [coupons, setCoupons] = useState<ICouponAvailable[]>([]);

  useEffect(() => {
    let cancelled = false;
    const limit = Math.min(Math.max(Number(config.limit) || 3, 1), 10);
    // Palette v1.1: optional explicit selection — configured order, coupons the
    // available list no longer carries are skipped (R1 per item).
    const explicitIds = numberIds(config.couponIds);
    // BARE array, no limit param — the renderer slices (spec §2.4).
    unwrap<ICouponAvailable[]>(baseAxios.get(`${SRV}/promotion/coupon/available`))
      .then(arr => {
        if (cancelled) return;
        const all = Array.isArray(arr) ? arr : [];
        if (explicitIds.length > 0) {
          const byId = new Map(all.map(c => [c.couponId, c]));
          const picked = explicitIds.map(id => byId.get(id)).filter(Boolean) as ICouponAvailable[];
          // An explicit pick is the admin's list — the default 3-slice only
          // applies when a limit was actually configured.
          setCoupons(config.limit != null ? picked.slice(0, limit) : picked);
        } else {
          setCoupons(all.slice(0, limit));
        }
      })
      .catch(() => {
        if (!cancelled) setCoupons([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (coupons.length === 0) return null;
  // v1.1 style variant: 'grid' = multi-column; anything else keeps the v1 strip.
  const grid = config.style === 'grid';
  const headline = typeof config.headline === 'string' && config.headline ? config.headline : null;
  return (
    <Section title={(config.title as string) ?? 'Coupons'}>
      {headline && <p className='lm-coupons__headline'>{headline}</p>}
      <div className={grid ? 'lm-coupons lm-coupons--grid' : 'lm-coupons'}>
        {coupons.map((c, i) => (
          <Link key={c.couponId ?? i} to='/user/coupons' className='lm-coupon text-decoration-none'>
            {/* Percent coupons (Wave-18) show the rate; flat renders exactly as v1. */}
            <div className='lm-coupon__amount'>{c.discountType === 1 ? `${c.discount}%` : `$${c.discount}`}</div>
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

/**
 * Palette v1.1 `groupon-strip` — active group-buy campaigns from
 * `/srv/promotion/combination/active` (bare array, the same endpoint the
 * /groupon page's interactive flow rides). Cards TEASE the campaign and link
 * to the PRODUCT page: checkout still charges retail until the Phase-3 priced
 * submit lands, so no "buy at the group price" promise is made here (the
 * recorded gating decision) — join/browse copy only.
 */
const GrouponStripC: React.FC<{ config: Record<string, unknown> }> = ({ config }) => {
  const [campaigns, setCampaigns] = useState<ICombination[]>([]);

  useEffect(() => {
    let cancelled = false;
    // maxItems 1–12, default 4 (palette v1.1).
    const maxItems = Math.min(Math.max(Number(config.maxItems) || 4, 1), 12);
    const explicitIds = numberIds(config.combinationIds);
    promotionApi
      .combinationActive()
      .then(arr => {
        if (cancelled) return;
        const all = Array.isArray(arr) ? arr : [];
        // Explicit ids filter in configured order; empty/absent = auto (all active).
        const picked =
          explicitIds.length > 0
            ? (explicitIds.map(id => all.find(c => c.combinationId === id)).filter(Boolean) as ICombination[])
            : all;
        setCampaigns(picked.slice(0, maxItems));
      })
      .catch(() => {
        // R1: fetch failure ⇒ component skipped.
        if (!cancelled) setCampaigns([]);
      });
    return () => {
      cancelled = true;
    };
  }, [config]);

  if (campaigns.length === 0) return null;
  return (
    <Section title={(config.title as string) ?? 'Group up & save'}>
      <div className='lm-rail'>
        {campaigns.map((g, i) => {
          const card = (
            <div className='card p-2 h-100' style={{ minWidth: 190 }}>
              {g.picUrl && <img src={g.picUrl} alt='' style={{ width: '100%', borderRadius: 6 }} loading='lazy' />}
              <div className='small text-truncate mt-1'>{g.title}</div>
              <div>
                <span className='fw-bold text-danger'>${g.combinationPrice}</span>
                {g.originalPrice != null && (
                  <span className='small text-muted text-decoration-line-through ms-1'>${g.originalPrice}</span>
                )}
              </div>
              {g.requiredMembers != null && <div className='small text-muted'>{g.requiredMembers}-person group</div>}
              <div className='small fw-semibold lm-groupon-teaser'>Group up &amp; save</div>
            </div>
          );
          return g.goodsId != null ? (
            <Link key={g.combinationId ?? i} to={productPath(g.goodsId, g.title)} className='text-decoration-none text-reset'>
              {card}
            </Link>
          ) : (
            <div key={g.combinationId ?? i}>{card}</div>
          );
        })}
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
  'groupon-strip': GrouponStripC,
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
