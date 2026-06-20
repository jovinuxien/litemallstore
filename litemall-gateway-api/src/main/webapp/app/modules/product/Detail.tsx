import React, { useEffect, useMemo, useState } from 'react';
import { Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { addItem } from 'app/shared/reducers/cartSlice';
import { goodId, priceNum } from 'app/components/userComponents/card/ProductCard';
import ProductCard from 'app/components/userComponents/card/ProductCard';
import { DetailProduct } from './productDetailSlice';
import { getProductDetail } from './productDetailSlice';
import { getRelatedGoods } from './relatedSlice';
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
  const { id } = useParams<{ id: string }>();

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
    }
  }, [dispatch, id]);

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
  const inStock = stock > 0;

  const pickVariant = (name: string, value: string, picUrl?: string) => {
    setSelected(prev => ({ ...prev, [name]: value }));
    if (picUrl) setActiveImage(picUrl);
  };

  // CJ products carry a "cj_<pid>" goodsId and a CJ variant id (vid) that exceeds JS
  // safe-integer range, so the vid must be read as the RAW string from goodsProductId —
  // never via productId() (which Number()-coerces and loses precision).
  const isCj = String(gid).startsWith('cj_');
  const rawVid = (() => {
    const raw = selectedSku?.goodsProductId as { id?: string } | string | undefined;
    const v = typeof raw === 'object' ? raw?.id : raw;
    return v != null ? String(v) : undefined;
  })();

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
    source: isCj ? 'cj_dropshipping' : undefined,
    vid: isCj ? rawVid : undefined,
  });

  const handleAddToCart = () => {
    if (!inStock) return;
    dispatch(addItem(buildCartItem()));
    navigate('/cart');
  };

  const handleBuyNow = () => {
    if (!inStock) return;
    dispatch(addItem(buildCartItem()));
    navigate('/checkout');
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
          {goods.brief && <p className='lm-pdp__brief'>{goods.brief}</p>}

          <div className='lm-pdp__pricebox'>
            <span className='lm-pdp__price'>
              US&nbsp;${fmt(retail)}
            </span>
            {hasDiscount && <span className='lm-pdp__orig'>US&nbsp;${fmt(counter)}</span>}
            {hasDiscount && <span className='lm-pdp__save'>You save {discountPct}%</span>}
            {goods.unit && <span className='lm-pdp__unit'>per {goods.unit}</span>}
          </div>

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
            {inStock ? `In stock${selectedSku ? ` · ${stock} available` : ''}` : 'Out of stock'}
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

          <ul className='lm-pdp__assurance'>
            <li>
              <i className='bi bi-truck' /> Fast dispatch
            </li>
            <li>
              <i className='bi bi-arrow-counterclockwise' /> 7-day returns
            </li>
            <li>
              <i className='bi bi-shield-check' /> Buyer protection
            </li>
          </ul>
        </aside>
      </div>

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
            {goods.detail ? (
              <div className='lm-pdp__detailhtml' dangerouslySetInnerHTML={{ __html: goods.detail }} />
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

      {/* Related products */}
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
    </div>
  );
};

export default ProductDetailView;
