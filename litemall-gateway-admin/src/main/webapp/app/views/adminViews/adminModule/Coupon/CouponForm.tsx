import { ICoupon } from 'app/shared/model/admin/promotion-system.model';
import { promotionOpMessage, useCreateCouponMutation, useReadCouponQuery, useUpdateCouponMutation } from 'app/shared/reducers/private/services/adminPromotionApi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a coupon against promotion-service
// (/srv/private/admin/promotion/coupon). Server requires a non-empty name; an
// exchange-code coupon (type 2) has its code generated server-side on create.
// timeType follows promotion's LitemallCouponTimeType: 0 = valid for `days`
// after claim, 1 = absolute startTime..endTime window.

const empty: ICoupon = {
  name: '',
  description: '',
  tag: '',
  total: 0,
  discount: 0,
  min: 0,
  limitPerUser: 1,
  type: 0,
  goodsType: 0,
  timeType: 0,
  days: 0,
  startTime: '',
  endTime: '',
};

const CouponForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadCouponQuery(id as string, { skip: !isEdit });
  const [createCoupon, { isLoading: creating }] = useCreateCouponMutation();
  const [updateCoupon, { isLoading: updating }] = useUpdateCouponMutation();

  const [form, setForm] = React.useState<ICoupon>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<ICoupon>) => setForm(prev => ({ ...prev, ...patch }));

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
    const body: ICoupon = {
      ...form,
      total: Number(form.total ?? 0),
      discount: Number(form.discount ?? 0),
      min: Number(form.min ?? 0),
      limitPerUser: Number(form.limitPerUser ?? 1),
      days: Number(form.days ?? 0),
    };
    const res = await (isEdit ? updateCoupon(body) : createCoupon(body));
    const msg = promotionOpMessage(res);
    if (msg) {
      setError(msg);
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

  const busy = creating || updating;
  // datetime-local wants 'YYYY-MM-DDTHH:mm'; the server returns full ISO.
  const dtLocal = (v?: string) => (v ? v.slice(0, 16) : '');

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit coupon #${id}` : 'New coupon'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }}>
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
        <div className='row'>
          <div className='col-md-3 mb-3'>
            <label className='form-label'>Discount (¥ off)</label>
            <input className='form-control' type='number' step='0.01' value={form.discount ?? 0} onChange={e => set({ discount: Number(e.target.value) })} />
          </div>
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
