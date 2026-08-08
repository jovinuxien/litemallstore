import { ICoupon } from 'app/shared/model/admin/promotion-system.model';
import {
  promotionOpMessage,
  promotionOpWarning,
  useCreateCouponMutation,
  useReadCouponQuery,
  useUpdateCouponMutation,
} from 'app/shared/reducers/private/services/adminPromotionApi';
import { useConsumePromoCandidateMutation, useGetInsightCategoriesQuery } from 'app/shared/reducers/private/services/insightApi';
import { fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { CouponPromoPrefill, createdRefId } from 'app/views/adminViews/adminModule/Insight/promoFormat';
import CouponGoodsPicker from './CouponGoodsPicker';
import { DISCOUNT_PERCENT, PERCENT_MAX, PERCENT_MIN, SCOPE_ALL, SCOPE_CATEGORY, SCOPE_GOODS, couponClientError } from './couponFormat';
import * as React from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';

// Create / edit a coupon against promotion-service
// (/srv/private/admin/promotion/coupon). Server requires a non-empty name; an
// exchange-code coupon (type 2) has its code generated server-side on create.
// timeType follows promotion's LitemallCouponTimeType: 0 = valid for `days`
// after claim, 1 = absolute startTime..endTime window.
//
// Wave 18: scope selector (all / category via the insight L1 roots, showing
// avg margin per root / specific products via the insight goods picker) sends
// goodsType + goodsValue; discount-type radio adds percent-off (rate 1–90 +
// optional $ cap). The promotion-side margin guard is a HARD BLOCK — its
// rejection message states the computed maximum and is surfaced VERBATIM; on
// success an `uncostedCount` warning (goods whose cost is not captured yet)
// is shown non-blocking.

const empty: ICoupon = {
  name: '',
  description: '',
  tag: '',
  total: 0,
  discount: 0,
  min: 0,
  limitPerUser: 1,
  type: 0,
  goodsType: SCOPE_ALL,
  goodsValue: undefined,
  discountType: 0,
  discountCap: undefined,
  timeType: 0,
  days: 0,
  startTime: '',
  endTime: '',
};

const CouponForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const location = useLocation();

  // Wave 19: the promo-suggestions panel opens this form prefilled via router
  // state; absent state changes nothing. Captured once — the suggestion only
  // seeds the initial form, the admin's edits win from then on.
  const [prefill] = React.useState<CouponPromoPrefill | null>(
    () => (!isEdit && (location.state as { promoPrefill?: CouponPromoPrefill } | null)?.promoPrefill) || null
  );

  const { data: existing, isLoading: loading } = useReadCouponQuery(id as string, { skip: !isEdit });
  const [createCoupon, { isLoading: creating }] = useCreateCouponMutation();
  const [updateCoupon, { isLoading: updating }] = useUpdateCouponMutation();
  const { data: categories, isLoading: categoriesLoading } = useGetInsightCategoriesQuery();
  const [consumeCandidate] = useConsumePromoCandidateMutation();

  const [form, setForm] = React.useState<ICoupon>(() => (prefill ? { ...empty, ...prefill.coupon } : empty));
  const [error, setError] = React.useState<string | null>(null);
  // Set when the save succeeded but the guard reported uncosted goods: the
  // form is replaced by a success panel so the warning is actually seen
  // (navigating away immediately would drop it).
  const [savedWarning, setSavedWarning] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<ICoupon>) => setForm(prev => ({ ...prev, ...patch }));

  const isPercent = (form.discountType ?? 0) === DISCOUNT_PERCENT;
  const scope = form.goodsType ?? SCOPE_ALL;
  const scopeIds = form.goodsValue ?? [];

  const toggleCategory = (categoryId: number) =>
    set({ goodsValue: scopeIds.includes(categoryId) ? scopeIds.filter(x => x !== categoryId) : [...scopeIds, categoryId] });

  // A stored category-scoped coupon may reference ids that are not L1 roots
  // (any level is legal server-side) — keep them visible and removable.
  const rootIds = new Set((categories?.list ?? []).map(c => c.categoryId));
  const nonRootIds = scope === SCOPE_CATEGORY ? scopeIds.filter(x => !rootIds.has(x)) : [];

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Name is required.');
      return;
    }
    if (form.timeType === 1 && (!form.startTime || !form.endTime)) {
      setError('An absolute-window coupon needs both start and end time.');
      return;
    }
    const clientError = couponClientError(form);
    if (clientError) {
      setError(clientError);
      return;
    }
    const body: ICoupon = {
      ...form,
      total: Number(form.total ?? 0),
      discount: Number(form.discount ?? 0),
      min: Number(form.min ?? 0),
      limitPerUser: Number(form.limitPerUser ?? 1),
      days: Number(form.days ?? 0),
      goodsType: scope,
      goodsValue: scope === SCOPE_ALL ? undefined : scopeIds,
      discountType: form.discountType ?? 0,
      discountCap: isPercent && form.discountCap != null ? Number(form.discountCap) : undefined,
    };
    const res = await (isEdit ? updateCoupon(body) : createCoupon(body));
    // Margin-guard (and any other) rejections surface VERBATIM — the message
    // states the computed maximum discount/rate for this scope.
    const msg = promotionOpMessage(res);
    if (msg) {
      setError(msg);
      return;
    }
    // Wave 19: opened from a promo suggestion → record the consumption with
    // the created coupon id. FAIL-SOFT by construction: the un-unwrapped
    // mutation promise never rejects, and the result is deliberately ignored —
    // a failed consume must never block or roll back the create.
    if (!isEdit && prefill) {
      consumeCandidate({ ...prefill.consume, refId: createdRefId(res, 'couponId') });
    }
    const warning = promotionOpWarning(res);
    if (warning) {
      setSavedWarning(warning);
      return;
    }
    navigate('/admin/promotion/coupon');
  };

  if (isEdit && loading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  if (savedWarning != null) {
    return (
      <div className='app-container' style={{ maxWidth: 720 }}>
        <div className='alert alert-success'>Coupon {isEdit ? 'updated' : 'created'}.</div>
        <div className='alert alert-warning'>{savedWarning}</div>
        <button className='btn btn-primary' type='button' onClick={() => navigate('/admin/promotion/coupon')}>
          Back to coupons
        </button>
      </div>
    );
  }

  const busy = creating || updating;
  // datetime-local wants 'YYYY-MM-DDTHH:mm'; the server returns full ISO.
  const dtLocal = (v?: string) => (v ? v.slice(0, 16) : '');

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit coupon #${id}` : 'New coupon'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      {/* noValidate: the percent rate input carries min/max as UI hints, but
          validation must flow through couponClientError so the user gets the
          same inline alert style as every other rule. */}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }} noValidate>
        <div className='row'>
          <div className='col-md-8 mb-3'>
            <label className='form-label'>Name *</label>
            <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Tag (badge)</label>
            <input className='form-control' value={form.tag ?? ''} onChange={e => set({ tag: e.target.value })} />
          </div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Description</label>
          <input className='form-control' value={form.description ?? ''} onChange={e => set({ description: e.target.value })} />
        </div>

        <div className='mb-2'>
          <label className='form-label d-block'>Discount type</label>
          <div className='form-check form-check-inline'>
            <input
              className='form-check-input'
              type='radio'
              name='discountType'
              id='discount-flat'
              checked={!isPercent}
              onChange={() => set({ discountType: 0, discountCap: undefined })}
            />
            <label className='form-check-label' htmlFor='discount-flat'>
              Flat amount ($ off)
            </label>
          </div>
          <div className='form-check form-check-inline'>
            <input
              className='form-check-input'
              type='radio'
              name='discountType'
              id='discount-percent'
              checked={isPercent}
              onChange={() => set({ discountType: DISCOUNT_PERCENT })}
            />
            <label className='form-check-label' htmlFor='discount-percent'>
              Percent off
            </label>
          </div>
        </div>
        <div className='row'>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>{isPercent ? `Rate (% off, ${PERCENT_MIN}–${PERCENT_MAX})` : 'Discount ($ off)'}</label>
            <input
              className='form-control'
              type='number'
              step={isPercent ? '1' : '0.01'}
              min={isPercent ? PERCENT_MIN : undefined}
              max={isPercent ? PERCENT_MAX : undefined}
              value={form.discount ?? 0}
              onChange={e => set({ discount: Number(e.target.value) })}
            />
          </div>
          {isPercent && (
            <div className='col-md-3 mb-3'>
              <label className='form-label'>Max discount ($, optional)</label>
              <input
                className='form-control'
                type='number'
                step='0.01'
                min='0'
                value={form.discountCap ?? ''}
                onChange={e => set({ discountCap: e.target.value === '' ? undefined : Number(e.target.value) })}
              />
            </div>
          )}
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Min spend</label>
            <input className='form-control' type='number' step='0.01' value={form.min ?? 0} onChange={e => set({ min: Number(e.target.value) })} />
          </div>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Total issued</label>
            <input className='form-control' type='number' value={form.total ?? 0} onChange={e => set({ total: Number(e.target.value) })} />
          </div>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Per-user limit</label>
            <input className='form-control' type='number' value={form.limitPerUser ?? 1} onChange={e => set({ limitPerUser: Number(e.target.value) })} />
          </div>
        </div>

        <div className='mb-3'>
          <label className='form-label'>Applies to</label>
          <select
            className='form-select'
            value={scope}
            onChange={e => set({ goodsType: Number(e.target.value), goodsValue: undefined })}
            aria-label='Coupon scope'
          >
            <option value={SCOPE_ALL}>All goods</option>
            <option value={SCOPE_CATEGORY}>A category</option>
            <option value={SCOPE_GOODS}>Specific products</option>
          </select>
        </div>
        {scope === SCOPE_CATEGORY && (
          <div className='mb-3 border rounded p-2'>
            <div className='text-muted small mb-2'>
              The coupon applies to goods in the checked categories (their whole subtree). Average margin per root helps judge how much discount the
              scope can carry.
            </div>
            {categoriesLoading ? (
              <span className='spinner-border spinner-border-sm text-primary' role='status' />
            ) : (
              (categories?.list ?? []).map(c => (
                <div className='form-check' key={c.categoryId}>
                  <input
                    className='form-check-input'
                    type='checkbox'
                    id={`coupon-cat-${c.categoryId}`}
                    checked={scopeIds.includes(c.categoryId)}
                    onChange={() => toggleCategory(c.categoryId)}
                  />
                  <label className='form-check-label' htmlFor={`coupon-cat-${c.categoryId}`}>
                    {c.name} <span className='text-muted small'>· avg margin {fmtPct(c.avgMarginPct)}</span>
                  </label>
                </div>
              ))
            )}
            {nonRootIds.length > 0 && (
              <div className='mt-2 d-flex flex-wrap gap-1'>
                <span className='text-muted small align-self-center'>Other category ids on this coupon:</span>
                {nonRootIds.map(x => (
                  <button
                    key={x}
                    type='button'
                    className='btn btn-sm btn-outline-secondary'
                    onClick={() => toggleCategory(x)}
                    title='Remove from coupon scope'
                  >
                    #{x} ✕
                  </button>
                ))}
              </div>
            )}
          </div>
        )}
        {scope === SCOPE_GOODS && (
          <div className='mb-3'>
            <CouponGoodsPicker value={scopeIds} onChange={ids => set({ goodsValue: ids })} />
          </div>
        )}

        <div className='row'>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Type</label>
            <select className='form-select' value={form.type ?? 0} onChange={e => set({ type: Number(e.target.value) })}>
              <option value={0}>General (claimable)</option>
              <option value={1}>On registration</option>
              <option value={2}>Exchange code</option>
            </select>
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Validity</label>
            <select className='form-select' value={form.timeType ?? 0} onChange={e => set({ timeType: Number(e.target.value) })}>
              <option value={0}>Relative (days after claim)</option>
              <option value={1}>Fixed date range</option>
            </select>
          </div>
          <div className='col-md-4 mb-3'>
            <label className='form-label'>Days valid (relative)</label>
            <input className='form-control' type='number' value={form.days ?? 0} onChange={e => set({ days: Number(e.target.value) })} disabled={form.timeType !== 0} />
          </div>
        </div>
        {form.timeType === 1 && (
          <div className='row'>
            <div className='col-md-6 mb-3'>
              <label className='form-label'>Valid from *</label>
              <input className='form-control' type='datetime-local' value={dtLocal(form.startTime)} onChange={e => set({ startTime: e.target.value })} />
            </div>
            <div className='col-md-6 mb-3'>
              <label className='form-label'>Valid to *</label>
              <input className='form-control' type='datetime-local' value={dtLocal(form.endTime)} onChange={e => set({ endTime: e.target.value })} />
            </div>
          </div>
        )}
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/promotion/coupon')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default CouponForm;
