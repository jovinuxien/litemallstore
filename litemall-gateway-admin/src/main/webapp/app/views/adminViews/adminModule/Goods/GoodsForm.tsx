import {
  GoodsAllinone,
  useCreateGoodsMutation,
  useGetAdminGoodsDetailQuery,
  useGetCatAndBrandQuery,
  useUpdateGoodsMutation,
} from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import { useFreightSelectListQuery } from 'app/shared/reducers/private/services/adminFreightApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a goods record ("essential + SKUs" scope): the main goods
// fields plus an editable per-SKU price/stock table. Specifications and
// attributes stay read-only (view them on the Detail page) — the backend
// update path only applies product rows whose updateTime is null, so this form
// strips timestamps from edited rows and leaves specs/attrs untouched.
//
// Create mode seeds SKU rows with a single default specification ("Standard"
// variants), matching what the backend create expects: every product row's
// `specifications` values must exist in the goods-level specification list.

interface SkuRow {
  id?: number;
  variant: string; // single spec value, e.g. 'Standard' or 'Large'
  price: string;
  number: string;
  url: string;
  raw?: Record<string, unknown>; // original product row (edit mode)
}

interface GoodsFields {
  name: string;
  goodsSn: string;
  categoryId: number | '';
  brandId: number | '';
  keywords: string;
  brief: string;
  unit: string;
  picUrl: string;
  gallery: string; // one URL per line
  counterPrice: string;
  sortOrder: string;
  tempId: number; // freight template; 0 = none (flat freight)
  isOnSale: boolean;
  isNew: boolean;
  isHot: boolean;
  detail: string;
}

const EMPTY: GoodsFields = {
  name: '',
  goodsSn: '',
  categoryId: '',
  brandId: '',
  keywords: '',
  brief: '',
  unit: '件',
  picUrl: '',
  gallery: '',
  counterPrice: '',
  sortOrder: '100',
  tempId: 0,
  isOnSale: true,
  isNew: true,
  isHot: false,
  detail: '',
};

const DEFAULT_SPEC_NAME = 'Specification';

// Detail payload as the backend actually returns it (goods + raw table rows).
interface RawDetail {
  goods?: Record<string, unknown>;
  specifications?: Record<string, unknown>[];
  products?: Record<string, unknown>[];
  attributes?: Record<string, unknown>[];
}

const str = (v: unknown): string => (v == null ? '' : String(v));
const stripTimes = (row: Record<string, unknown>): Record<string, unknown> => {
  const { addTime, updateTime, ...rest } = row;
  return rest;
};

const GoodsForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: detail, isLoading: loadingDetail } = useGetAdminGoodsDetailQuery(id as string, { skip: !isEdit });
  const { data: catAndBrand } = useGetCatAndBrandQuery();
  const { data: freightTemplates } = useFreightSelectListQuery();
  const [createGoods, { isLoading: creating }] = useCreateGoodsMutation();
  const [updateGoods, { isLoading: updating }] = useUpdateGoodsMutation();

  const [form, setForm] = React.useState<GoodsFields>(EMPTY);
  const [skus, setSkus] = React.useState<SkuRow[]>([{ variant: 'Standard', price: '', number: '0', url: '' }]);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (!isEdit || !detail) return;
    const raw = detail as unknown as RawDetail;
    const goods = raw.goods ?? {};
    setForm({
      name: str(goods.name),
      goodsSn: str(goods.goodsSn),
      categoryId: goods.categoryId != null ? Number(goods.categoryId) : '',
      brandId: goods.brandId != null ? Number(goods.brandId) : '',
      keywords: str(goods.keywords),
      brief: str(goods.brief),
      unit: str(goods.unit) || '件',
      picUrl: str(goods.picUrl),
      gallery: Array.isArray(goods.gallery) ? (goods.gallery as string[]).join('\n') : '',
      counterPrice: str(goods.counterPrice),
      sortOrder: str(goods.sortOrder ?? 100),
      tempId: goods.tempId != null ? Number(goods.tempId) : 0,
      isOnSale: Boolean(goods.isOnSale),
      isNew: Boolean(goods.isNew),
      isHot: Boolean(goods.isHot),
      detail: str(goods.detail),
    });
    setSkus(
      (raw.products ?? []).map(p => ({
        id: p.id != null ? Number(p.id) : undefined,
        variant: Array.isArray(p.specifications) ? (p.specifications as string[]).join(' / ') : '',
        price: str(p.price),
        number: str(p.number ?? 0),
        url: str(p.url),
        raw: p,
      }))
    );
  }, [isEdit, detail]);

  const set = (patch: Partial<GoodsFields>) => setForm(prev => ({ ...prev, ...patch }));
  const setSku = (i: number, patch: Partial<SkuRow>) => setSkus(prev => prev.map((row, idx) => (idx === i ? { ...row, ...patch } : row)));

  const buildGoods = (): Record<string, unknown> => ({
    ...(isEdit && detail ? stripTimes(((detail as unknown as RawDetail).goods ?? {}) as Record<string, unknown>) : {}),
    name: form.name.trim(),
    goodsSn: form.goodsSn.trim(),
    categoryId: form.categoryId === '' ? 0 : Number(form.categoryId),
    brandId: form.brandId === '' ? 0 : Number(form.brandId),
    keywords: form.keywords.trim(),
    brief: form.brief.trim(),
    unit: form.unit.trim(),
    picUrl: form.picUrl.trim(),
    gallery: form.gallery
      .split('\n')
      .map(s => s.trim())
      .filter(Boolean),
    counterPrice: form.counterPrice === '' ? 0 : Number(form.counterPrice),
    sortOrder: Number(form.sortOrder || 100),
    tempId: Number(form.tempId ?? 0),
    isOnSale: form.isOnSale,
    isNew: form.isNew,
    isHot: form.isHot,
    detail: form.detail,
  });

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name.trim() || !form.goodsSn.trim()) {
      setError('Name and goods SN are required.');
      return;
    }
    if (skus.length === 0) {
      setError('At least one SKU row is required.');
      return;
    }
    for (const sku of skus) {
      if (sku.price === '' || Number.isNaN(Number(sku.price)) || Number(sku.price) < 0) {
        setError('Every SKU needs a valid price.');
        return;
      }
      if (sku.number === '' || Number.isNaN(Number(sku.number)) || Number(sku.number) < 0) {
        setError('Every SKU needs a valid stock number.');
        return;
      }
      if (!isEdit && !sku.variant.trim()) {
        setError('Every SKU needs a variant label (e.g. Standard).');
        return;
      }
    }

    let body: GoodsAllinone;
    if (isEdit) {
      const raw = detail as unknown as RawDetail;
      body = {
        goods: buildGoods(),
        // Only price/number/url are editable server-side; stripping the
        // timestamps marks the rows as "changed" for the update path.
        products: skus.map(sku => ({
          ...stripTimes(sku.raw ?? {}),
          price: Number(sku.price),
          number: Number(sku.number),
          url: sku.url.trim(),
        })),
        // Passed through untouched (updateTime present → backend skips them).
        specifications: raw.specifications ?? [],
        attributes: raw.attributes ?? [],
      };
    } else {
      const variants = skus.map(sku => sku.variant.trim());
      body = {
        goods: buildGoods(),
        specifications: variants.map(variant => ({ specification: DEFAULT_SPEC_NAME, value: variant, picUrl: '' })),
        products: skus.map(sku => ({
          specifications: [sku.variant.trim()],
          price: Number(sku.price),
          number: Number(sku.number),
          url: sku.url.trim() || form.picUrl.trim(),
        })),
        attributes: [],
      };
    }

    const res = await (isEdit ? updateGoods(body) : createGoods(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/goods');
  };

  if (isEdit && loadingDetail) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const busy = creating || updating;
  const categories = catAndBrand?.categoryList ?? [];
  const brands = catAndBrand?.brandList ?? [];

  // CJ-sourced goods carry the CJ product id on the goods row (litemall_goods
  // .cj_pid → cjPid; the form has no other CJ marker today — the raw goods
  // record is inspected directly, with a `source === 'cj'` fallback in case the
  // backend adds that field). CJ freight is quoted live from CJ logistics
  // (freightCalculate), so a local freight template never applies.
  const rawGoods = isEdit && detail ? (((detail as unknown as RawDetail).goods ?? {}) as Record<string, unknown>) : {};
  const isCjGoods = Boolean(rawGoods.cjPid) || rawGoods.source === 'cj';

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit goods #${id}` : 'New goods'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 860 }}>
        <div className='row'>
          <div className='col-md-8 mb-3'>
            <label className='form-label'>Name *</label>
            <input className='form-control' value={form.name} onChange={e => set({ name: e.target.value })} />
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Goods SN *</label>
            <input className='form-control' value={form.goodsSn} onChange={e => set({ goodsSn: e.target.value })} />
          </div>
        </div>

        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Category</label>
            <select
              className='form-select'
              value={form.categoryId}
              onChange={e => set({ categoryId: e.target.value === '' ? '' : Number(e.target.value) })}
            >
              <option value=''>— none —</option>
              {categories.map(l1 =>
                l1.children && l1.children.length > 0 ? (
                  <optgroup key={l1.value} label={l1.label}>
                    {l1.children.map(l2 => (
                      <option key={l2.value} value={l2.value}>
                        {l2.label}
                      </option>
                    ))}
                  </optgroup>
                ) : (
                  <option key={l1.value} value={l1.value}>
                    {l1.label}
                  </option>
                )
              )}
            </select>
          </div>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Brand</label>
            <select className='form-select' value={form.brandId} onChange={e => set({ brandId: e.target.value === '' ? '' : Number(e.target.value) })}>
              <option value=''>— none —</option>
              {brands.map(b => (
                <option key={b.value} value={b.value}>
                  {b.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Freight template</label>
            <select
              className='form-select'
              value={form.tempId}
              disabled={isCjGoods}
              onChange={e => set({ tempId: Number(e.target.value) })}
            >
              <option value={0}>None (flat freight)</option>
              {(freightTemplates ?? []).map(t => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
            {isCjGoods && (
              <div className='form-text'>
                CJ-sourced goods: freight is quoted live from CJ logistics, so freight templates do not apply.
              </div>
            )}
          </div>
        </div>

        <div className='row'>
          <div className='col-md-8 mb-3'>
            <label className='form-label'>Brief</label>
            <input className='form-control' value={form.brief} onChange={e => set({ brief: e.target.value })} />
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Keywords</label>
            <input className='form-control' placeholder='comma,separated' value={form.keywords} onChange={e => set({ keywords: e.target.value })} />
          </div>
        </div>

        <div className='row'>
          <div className='col-md-8 mb-3'>
            <label className='form-label'>Main image URL</label>
            <input className='form-control' value={form.picUrl} onChange={e => set({ picUrl: e.target.value })} />
            {form.picUrl && <img src={form.picUrl} alt='' className='cell-thumb mt-2' />}
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label'>Counter price</label>
            <input className='form-control' type='number' step='0.01' min='0' value={form.counterPrice} onChange={e => set({ counterPrice: e.target.value })} />
          </div>
          <div className='col-md-2 mb-3'>
            <label className='form-label'>Unit</label>
            <input className='form-control' value={form.unit} onChange={e => set({ unit: e.target.value })} />
          </div>
        </div>

        <div className='mb-3'>
          <label className='form-label'>Gallery (one image URL per line)</label>
          <textarea className='form-control' rows={3} value={form.gallery} onChange={e => set({ gallery: e.target.value })} />
        </div>

        <div className='d-flex gap-4 mb-3'>
          <div className='form-check'>
            <input className='form-check-input' type='checkbox' id='isOnSale' checked={form.isOnSale} onChange={e => set({ isOnSale: e.target.checked })} />
            <label className='form-check-label' htmlFor='isOnSale'>
              On sale
            </label>
          </div>
          <div className='form-check'>
            <input className='form-check-input' type='checkbox' id='isNew' checked={form.isNew} onChange={e => set({ isNew: e.target.checked })} />
            <label className='form-check-label' htmlFor='isNew'>
              New
            </label>
          </div>
          <div className='form-check'>
            <input className='form-check-input' type='checkbox' id='isHot' checked={form.isHot} onChange={e => set({ isHot: e.target.checked })} />
            <label className='form-check-label' htmlFor='isHot'>
              Hot
            </label>
          </div>
          <div style={{ width: 140 }}>
            <div className='input-group input-group-sm'>
              <span className='input-group-text'>Sort</span>
              <input className='form-control' type='number' value={form.sortOrder} onChange={e => set({ sortOrder: e.target.value })} />
            </div>
          </div>
        </div>

        <div className='box-card'>
          <div className='box-card-header d-flex justify-content-between align-items-center'>
            <span>SKUs (price / stock{isEdit ? ' / image — variants fixed' : ''})</span>
            {!isEdit && (
              <button
                type='button'
                className='btn btn-sm btn-outline-success'
                onClick={() => setSkus(prev => [...prev, { variant: '', price: '', number: '0', url: '' }])}
              >
                + Add SKU
              </button>
            )}
          </div>
          <div className='box-card-body'>
            <table className='el-table'>
              <thead>
                <tr>
                  <th>Variant</th>
                  <th style={{ width: 140 }}>Price</th>
                  <th style={{ width: 120 }}>Stock</th>
                  <th>Image URL</th>
                  {!isEdit && <th style={{ width: 60 }} />}
                </tr>
              </thead>
              <tbody>
                {skus.map((sku, i) => (
                  <tr key={sku.id ?? `new-${i}`}>
                    <td>
                      {isEdit ? (
                        <span>{sku.variant || '—'}</span>
                      ) : (
                        <input className='form-control form-control-sm' placeholder='Standard' value={sku.variant} onChange={e => setSku(i, { variant: e.target.value })} />
                      )}
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        step='0.01'
                        min='0'
                        value={sku.price}
                        onChange={e => setSku(i, { price: e.target.value })}
                      />
                    </td>
                    <td>
                      <input
                        className='form-control form-control-sm'
                        type='number'
                        min='0'
                        value={sku.number}
                        onChange={e => setSku(i, { number: e.target.value })}
                      />
                    </td>
                    <td>
                      <input className='form-control form-control-sm' value={sku.url} onChange={e => setSku(i, { url: e.target.value })} />
                    </td>
                    {!isEdit && (
                      <td className='text-end'>
                        <button
                          type='button'
                          className='btn btn-sm btn-outline-danger'
                          disabled={skus.length <= 1}
                          onClick={() => setSkus(prev => prev.filter((_, idx) => idx !== i))}
                        >
                          ×
                        </button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
            <div className='small text-muted mt-1'>The goods retail price is set server-side to the lowest SKU price.</div>
          </div>
        </div>

        <div className='mb-3'>
          <label className='form-label'>Detail (HTML)</label>
          <textarea className='form-control' rows={6} value={form.detail} onChange={e => set({ detail: e.target.value })} />
        </div>

        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/goods')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default GoodsForm;
