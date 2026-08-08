import React, { useEffect, useMemo, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { userApi } from 'app/shared/api';
import { addItem } from 'app/shared/reducers/cartSlice';
import { goodId, priceNum } from 'app/components/userComponents/card/ProductCard';
import ProductCard from 'app/components/userComponents/card/ProductCard';
import { setPageTitle, resetPageTitle } from 'app/shared/util/pageTitle';
import { trackAddToCart, trackProductView } from 'app/shared/tracking/ecommerce';
import { goodsIdFromRoute } from 'app/shared/util/slug';
import { briefToText } from 'app/shared/util/briefText';
import { DetailProduct } from './productDetailSlice';
import { getProductDetail } from './productDetailSlice';
import { getRelatedGoods } from './relatedSlice';
import CollectButton from './productDetailComponent/CollectButton';
import CouponStrip from './productDetailComponent/CouponStrip';
import DealBanner from './productDetailComponent/DealBanner';
import GroupBuyStrip from './productDetailComponent/GroupBuyStrip';
import Reviews from './productDetailComponent/Reviews';
import './Detail.scss';

const fmt = (n: number): string => n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const productId = (p: DetailProduct): number | undefined => {
  const raw = p.goodsProductId;
  const idStr = typeof raw === 'object' ? raw?.id : raw;
  const n = Number(idStr);
  return Number.isFinite(n) ? n : undefined;
};

const sameSpecs = (a: string[] = [], b: string[] = []): boolean => a.length === b.length && a.every((v, i) => v === b[i]);

/**
 * Amazon-style product detail page (Teal & Coral theme). Renders the full
 * goods-management `/srv/goods/detail` payload: gallery, variant selectors
 * resolved against the SKU list, a sticky buy box, and tabbed
 * Description / Specifications sections plus a related-products row.
 */
const ProductDetailView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id: routeId } = useParams<{ id: string }>();
  // Slugged URLs (`/product/123-some-name`, Wave-13) carry the id in the
  // leading digits; bare numeric and legacy CJ ids pass through verbatim.
  const id = goodsIdFromRoute(routeId);
  // Wave-21 group-buy: a shopper arriving from the /groupon/:id landing holds
  // a group slot (?pinkId=<own slot id>) — Buy-now then carries it into
  // checkout, where the order service prices the line at the group price.
  const [searchParams] = useSearchParams();
  const heldPinkId = searchParams.get('pinkId');

  const { data, loading, errorMessage } = useAppSelector(state => state.productDetail);
  const related = useAppSelector(state => state.related.data.list);
  const { goods, products, specifications, attributes } = data;

  const [quantity, setQuantity] = useState(1);
  const [activeImage, setActiveImage] = useState<string>('');
  const [selected, setSelected] = useState<Record<string, string>>({});
  const [tab, setTab] = useState<'description' | 'specs'>('description');

  useEffect(() => {
    if (id) {
      // Pass the route id through verbatim — it may be a CJ id ("cj_<pid>"),
      // not a number. Coercing with Number() turned those into NaN and fired
      // /srv/goods/detail?id=NaN. The id is the OCS/document key the backend
      // keys on, string or numeric.
      dispatch(getProductDetail(id));
      dispatch(getRelatedGoods(id));
      setQuantity(1);
      // Record the visit in the customer's footprint (browsing history) —
      // fire-and-forget, signed-in only; the backend dedupes same goods/day
      // and the call is silently dropped until goods-management ships it
      // (docs/handoff-goods-management-engagement.md).
      if (sessionStorage.getItem('customerToken')) {
        userApi.footprintRecord(id).catch(() => undefined);
      }
    }
  }, [dispatch, id]);

  // Tab title follows the product on client-side navigation (a full page load
  // already arrives with the edge-injected title).
  useEffect(() => {
    if (goods?.goodsName) setPageTitle(goods.goodsName);
    return resetPageTitle;
  }, [goods?.goodsName]);

  // Conversion funnel (Wave 15): one ProductView per loaded goods, change-guarded
  // on the resolved goods id so re-renders and variant picks never re-fire it.
  const loadedGid = goods ? goodId(goods) : null;
  useEffect(() => {
    if (loadedGid != null && goods) {
      trackProductView({ id: String(loadedGid), name: goods.goodsName ?? '', price: priceNum(goods.retailPrice) });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loadedGid]);

  // Option groups: preserve backend order, collect distinct values per group.
  const specGroups = useMemo(() => {
    const groups: { name: string; values: { value: string; picUrl?: string }[] }[] = [];
    (specifications ?? []).forEach(s => {
      let g = groups.find(x => x.name === s.specifications);
      if (!g) {
        g = { name: s.specifications, values: [] };
        groups.push(g);
      }
      if (!g.values.some(v => v.value === s.value)) g.values.push({ value: s.value, picUrl: s.picUrl });
    });
    return groups;
  }, [specifications]);

  // Default the variant selection to the first value of every group.
  useEffect(() => {
    if (specGroups.length) {
      const init: Record<string, string> = {};
      specGroups.forEach(g => {
        if (g.values[0]) init[g.name] = g.values[0].value;
      });
      setSelected(init);
    }
  }, [specGroups]);

  // The SKU whose spec tuple matches the current selection (ordered by group).
  const selectedSku = useMemo<DetailProduct | undefined>(() => {
    if (!products?.length) return undefined;
    const wanted = specGroups.map(g => selected[g.name]);
    return products.find(p => sameSpecs(p.specifications, wanted)) ?? products[0];
  }, [products, specGroups, selected]);

  const gallery = useMemo(() => {
    const imgs = [goods?.picUrl, ...(goods?.gallery ?? [])].filter((x): x is string => !!x);
    return Array.from(new Set(imgs));
  }, [goods]);

  // Description = the first TWO images out of the goods.detail HTML blob, text dropped
  // (the raw supplier HTML — CJ especially — is a wall of duplicated text and imagery).
  // Also removes the dangerouslySetInnerHTML raw-HTML injection.
  const detailImages = useMemo(() => {
    if (!goods?.detail) return [] as string[];
    try {
      const doc = new DOMParser().parseFromString(goods.detail, 'text/html');
      return Array.from(doc.querySelectorAll('img'))
        .map(img => img.getAttribute('src') ?? img.getAttribute('data-src') ?? '')
        .filter(Boolean)
        .filter((src, i, arr) => arr.indexOf(src) === i)
        .slice(0, 2);
    } catch {
      return [] as string[];
    }
  }, [goods?.detail]);

  // Brief = plain text only — 5.3k on-sale CJ goods carry a raw supplier HTML
  // blob in this column, which used to render as literal "<p><img…" text
  // right under the title.
  const briefText = useMemo(() => briefToText(goods?.brief), [goods?.brief]);

  useEffect(() => {
    setActiveImage(goods?.picUrl ?? '');
  }, [goods?.picUrl]);

  if (loading === 'pending') {
    return (
      <div className='lm-pdp__center'>
        <Spinner animation='border' />
      </div>
    );
  }

  if (!goods || goodId(goods) == null) {
    return <div className='lm-pdp__center lm-pdp__notfound'>{errorMessage ?? 'Product not found.'}</div>;
  }

  const gid = goodId(goods);
  const skuPrice = selectedSku ? priceNum(selectedSku.price) : 0;
  const retail = skuPrice || priceNum(goods.retailPrice);
  const counter = priceNum(goods.counterPrice);
  const hasDiscount = counter > retail && retail > 0;
  const discountPct = hasDiscount ? Math.round(((counter - retail) / counter) * 100) : 0;
  const stock = selectedSku?.number ?? products.reduce((s, p) => s + (p.number ?? 0), 0);
  // Off-sale goods (e.g. the deactivated legacy catalog) stay reachable by
  // direct URL but must not be buyable — the order service rejects them anyway;
  // this keeps the UI honest. Missing field ⇒ treat as on sale.
  const onSale = (goods as { onSale?: boolean }).onSale !== false;
  const inStock = onSale && stock > 0;

  const pickVariant = (name: string, value: string, picUrl?: string) => {
    setSelected(prev => ({ ...prev, [name]: value }));
    if (picUrl) setActiveImage(picUrl);
  };

  // CJ Dropshipping lines now live in the native catalog (source 'cj'); the legacy DB-served
  // detail page still tags them 'cj_dropshipping'. Either marks the line CJ so checkout routes it
  // to the dropship endpoint — by its native productId, off which the order service recovers the
  // real CJ vid (no cj_<pid> id, no vid-as-productId hack).
  const isCj = goods.source === 'cj' || goods.source === 'cj_dropshipping';

  const buildCartItem = () => ({
    id: productId(selectedSku ?? ({} as DetailProduct)) ?? gid,
    goodsId: String(gid),
    goodsName: goods.goodsName ?? '',
    productId: productId(selectedSku ?? ({} as DetailProduct)),
    price: retail,
    number: quantity,
    picUrl: activeImage || goods.picUrl,
    specifications: specGroups.map(g => selected[g.name]).filter(Boolean),
    checked: true,
    source: isCj ? goods.source : undefined,
  });

  // Funnel add-to-cart (Wave 15): the added line + its line total. Display
  // money only — the charged figures always come from the server.
  const trackAdd = () => {
    trackAddToCart({ id: String(gid), name: goods.goodsName ?? '', price: retail, quantity }, retail * quantity);
  };

  const handleAddToCart = () => {
    if (!inStock) return;
    dispatch(addItem(buildCartItem()));
    trackAdd();
    navigate('/cart');
  };

  const handleBuyNow = () => {
    if (!inStock) return;
    dispatch(addItem(buildCartItem()));
    trackAdd();
    // A held group slot rides into checkout — submit charges the group price
    // server-side (a stale slot is rejected there with a typed message).
    navigate(heldPinkId ? `/checkout?pinkId=${heldPinkId}` : '/checkout');
  };

  return (
    <div className='lm-pdp'>
      <div className='lm-pdp__top'>
        {/* Gallery */}
        <section className='lm-pdp__gallery'>
          <div className='lm-pdp__stage'>
            <img src={activeImage} alt={goods.goodsName} />
            {hasDiscount && <span className='lm-pdp__badge'>-{discountPct}%</span>}
          </div>
          {gallery.length > 1 && (
            <div className='lm-pdp__thumbs'>
              {gallery.map(img => (
                <button
                  key={img}
                  type='button'
                  className={`lm-pdp__thumb${img === activeImage ? ' is-active' : ''}`}
                  onMouseEnter={() => setActiveImage(img)}
                  onClick={() => setActiveImage(img)}
                >
                  <img src={img} alt='' />
                </button>
              ))}
            </div>
          )}
        </section>

        {/* Title / price / variants */}
        <section className='lm-pdp__info'>
          <h1 className='lm-pdp__title'>{goods.goodsName}</h1>
          {briefText && <p className='lm-pdp__brief'>{briefText}</p>}

          <div className='lm-pdp__pricebox'>
            <span className='lm-pdp__price'>
              US&nbsp;${fmt(retail)}
            </span>
            {hasDiscount && <span className='lm-pdp__orig'>US&nbsp;${fmt(counter)}</span>}
            {hasDiscount && <span className='lm-pdp__save'>You save {discountPct}%</span>}
            {goods.unit && <span className='lm-pdp__unit'>per {goods.unit}</span>}
          </div>

          <DealBanner goodsId={gid} isCj={isCj} />

          {/* Wave-21 group-buy entry — renders only when an active combination
              campaign covers this goods. Adds the SELECTED variant to the cart
              before routing into /checkout?pinkId=. */}
          <GroupBuyStrip
            goodsId={gid}
            inStock={inStock}
            heldPinkId={heldPinkId}
            prepareCart={() => {
              dispatch(addItem(buildCartItem()));
              trackAdd();
            }}
          />

          {/* Receivable coupons (litemall-vue coupon row); hidden until live. */}
          <CouponStrip />

          {specGroups.map(g => (
            <div key={g.name} className='lm-pdp__optgroup'>
              <div className='lm-pdp__optlabel'>
                {g.name}: <strong>{selected[g.name]}</strong>
              </div>
              <div className='lm-pdp__opts'>
                {g.values.map(v => (
                  <button
                    key={v.value}
                    type='button'
                    className={`lm-pdp__opt${selected[g.name] === v.value ? ' is-active' : ''}`}
                    onClick={() => pickVariant(g.name, v.value, v.picUrl)}
                  >
                    {v.picUrl && <img src={v.picUrl} alt='' />}
                    {v.value}
                  </button>
                ))}
              </div>
            </div>
          ))}

          {attributes && attributes.length > 0 && (
            <ul className='lm-pdp__highlights'>
              {attributes.slice(0, 4).map((a, i) => (
                <li key={`${a.attributeName}-${i}`}>
                  <span>{a.attributeName}</span>
                  <strong>{a.attributeValue}</strong>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Buy box */}
        <aside className='lm-pdp__buybox'>
          <div className='lm-pdp__buyprice'>US&nbsp;${fmt(retail)}</div>
          <div className={`lm-pdp__stock${inStock ? ' in' : ' out'}`}>
            {!onSale
              ? 'Currently unavailable'
              : inStock
                ? `In stock${selectedSku ? ` · ${stock} available` : ''}`
                : 'Out of stock'}
          </div>

          <div className='lm-pdp__qty'>
            <span>Qty</span>
            <button type='button' onClick={() => setQuantity(q => Math.max(1, q - 1))} disabled={quantity <= 1}>
              −
            </button>
            <input
              type='number'
              min={1}
              max={inStock ? stock : 1}
              value={quantity}
              onChange={e => setQuantity(Math.max(1, Math.min(inStock ? stock : 1, parseInt(e.target.value, 10) || 1)))}
            />
            <button type='button' onClick={() => setQuantity(q => Math.min(inStock ? stock : 1, q + 1))} disabled={quantity >= stock}>
              +
            </button>
          </div>

          <button type='button' className='lm-pdp__addcart' disabled={!inStock} onClick={handleAddToCart}>
            <i className='bi bi-cart-plus me-1' /> Add to cart
          </button>
          <button type='button' className='lm-pdp__buynow' disabled={!inStock} onClick={handleBuyNow}>
            Buy now
          </button>

          <CollectButton goodsId={gid} />

          <ul className='lm-pdp__assurance'>
            <li>
              <i className='bi bi-truck' /> Fast dispatch
            </li>
            <li>
              <i className='bi bi-arrow-counterclockwise' /> 30-day returns
            </li>
            <li>
              <i className='bi bi-shield-check' /> Buyer protection
            </li>
          </ul>
        </aside>
      </div>

      {/* Related products — surfaced ahead of the description imagery and
          reviews, the Amazon PDP order. */}
      {related && related.length > 0 && (
        <section className='lm-pdp__related'>
          <h3 className='lm-pdp__relatedtitle'>You may also like</h3>
          <div className='lm-pdp__relatedgrid'>
            {related
              .filter(r => goodId(r) !== gid)
              .slice(0, 6)
              .map(r => (
                <ProductCard key={goodId(r)} product={r} />
              ))}
          </div>
        </section>
      )}

      {/* Tabs: description + full spec sheet */}
      <div className='lm-pdp__tabs'>
        <div className='lm-pdp__tabbar'>
          <button type='button' className={tab === 'description' ? 'is-active' : ''} onClick={() => setTab('description')}>
            Description
          </button>
          <button type='button' className={tab === 'specs' ? 'is-active' : ''} onClick={() => setTab('specs')}>
            Specifications {attributes?.length ? `(${attributes.length})` : ''}
          </button>
        </div>

        {tab === 'description' && (
          <div className='lm-pdp__tabpanel'>
            {detailImages.length > 0 ? (
              detailImages.map(src => (
                <img
                  key={src}
                  className='lm-pdp__detailimg'
                  src={src}
                  alt={goods.goodsName ?? 'Product'}
                  loading='lazy'
                  onError={e => (e.currentTarget.style.display = 'none')}
                />
              ))
            ) : briefText ? (
              <p>{briefText}</p>
            ) : (
              <p className='text-muted'>No description provided.</p>
            )}
          </div>
        )}

        {tab === 'specs' && (
          <div className='lm-pdp__tabpanel'>
            {attributes && attributes.length > 0 ? (
              <table className='lm-pdp__spectable'>
                <tbody>
                  {attributes.map((a, i) => (
                    <tr key={`${a.attributeName}-${i}`}>
                      <th>{a.attributeName}</th>
                      <td>{a.attributeValue}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p className='text-muted'>No specifications listed for this product.</p>
            )}
          </div>
        )}
      </div>

      {/* Customer reviews (litemall-vue comment list); hidden until live. */}
      <Reviews goodsId={gid} />
    </div>
  );
};

export default ProductDetailView;
