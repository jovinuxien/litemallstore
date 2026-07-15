import {
  dealOpMessage,
  DealUpdateBody,
  useCreateDealMutation,
  useReadDealQuery,
  useUpdateDealMutation,
} from 'app/shared/reducers/private/services/adminDealApi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a flash deal (/srv/private/admin/deal). Create posts
// {goodsId, dealPrice, startTime, stopTime, stock}; update posts {id, ...}
// with only the editable fields (goodsId is immutable — the input is disabled
// in edit mode). startTime/stopTime are sent as ISO local datetime strings
// WITHOUT a timezone suffix, e.g. "2026-07-15T18:00:00" (Jackson
// LocalDateTime). Backend validation errors surface inline via errmsg:
// errno 650 = invalid, 651 = conflict/overlapping window or live-immutable
// field, 652 = CJ-sourced goods refused.

interface DealEdit {
  goodsId: string;
  dealPrice: string;
  startTime: string; // datetime-local value, 'YYYY-MM-DDTHH:mm'
  stopTime: string;
  stock: string;
  enabled: boolean;
}

const empty: DealEdit = { goodsId: '', dealPrice: '', startTime: '', stopTime: '', stock: '0', enabled: true };

// datetime-local wants 'YYYY-MM-DDTHH:mm'; the server returns full ISO.
const dtLocal = (v?: string) => (v ? v.slice(0, 16) : '');
// datetime-local value → ISO LocalDateTime with seconds, no timezone suffix.
const toIsoLocal = (v: string) => (v.length === 16 ? `${v}:00` : v);

const DealForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadDealQuery(id as string, { skip: !isEdit });
  const [createDeal, { isLoading: creating }] = useCreateDealMutation();
  const [updateDeal, { isLoading: updating }] = useUpdateDealMutation();

  const [form, setForm] = React.useState<DealEdit>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) {
      setForm({
        goodsId: existing.goodsId != null ? String(existing.goodsId) : '',
        dealPrice: existing.dealPrice != null ? String(existing.dealPrice) : '',
        startTime: dtLocal(existing.startTime),
        stopTime: dtLocal(existing.stopTime),
        stock: existing.stock != null ? String(existing.stock) : '0',
        enabled: Boolean(existing.enabled),
      });
    }
  }, [isEdit, existing]);

  const set = (patch: Partial<DealEdit>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const goodsId = Number(form.goodsId);
    const dealPrice = Number(form.dealPrice);
    const stock = Number(form.stock || 0);
    if (!isEdit && (!form.goodsId.trim() || !Number.isInteger(goodsId) || goodsId <= 0)) {
      setError('Goods id must be a positive integer.');
      return;
    }
    if (!form.dealPrice.trim() || !Number.isFinite(dealPrice) || dealPrice <= 0) {
      setError('Deal price must be greater than 0.');
      return;
    }
    if (!Number.isInteger(stock) || stock < 0) {
      setError('Stock must be 0 (uncapped) or a positive integer.');
      return;
    }
    if (!form.startTime || !form.stopTime) {
      setError('Both start and stop time are required.');
      return;
    }
    if (form.stopTime <= form.startTime) {
      setError('Stop time must be after start time.');
      return;
    }
    const startTime = toIsoLocal(form.startTime);
    const stopTime = toIsoLocal(form.stopTime);
    let res;
    if (isEdit) {
      const body: DealUpdateBody = { id: Number(id), dealPrice, startTime, stopTime, stock, enabled: form.enabled };
      res = await updateDeal(body);
    } else {
      res = await createDeal({ goodsId, dealPrice, startTime, stopTime, stock });
    }
    // errno 650 (invalid) / 651 (conflict or live-immutable) / 652 (CJ goods
    // refused) all carry their message in errmsg — surface it verbatim.
    const msg = dealOpMessage(res);
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/promotion/deal');
  };

  if (isEdit && loading) {
    return (
      <div className='app-container text-center p-5'>
        <span className='spinner-border text-primary' role='status' />
      </div>
    );
  }

  const busy = creating || updating;

  return (
    <div className='app-container'>
      <h5 className='mb-3'>{isEdit ? `Edit flash deal #${id}` : 'New flash deal'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }}>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Goods id *</label>
            <input
              className='form-control'
              type='number'
              min='1'
              step='1'
              value={form.goodsId}
              onChange={e => set({ goodsId: e.target.value })}
              disabled={isEdit}
            />
            <div className='form-text'>local goods id — CJ-sourced goods are refused</div>
          </div>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Deal price *</label>
            <input
              className='form-control'
              type='number'
              step='0.01'
              min='0'
              value={form.dealPrice}
              onChange={e => set({ dealPrice: e.target.value })}
            />
          </div>
        </div>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Start time *</label>
            <input className='form-control' type='datetime-local' value={form.startTime} onChange={e => set({ startTime: e.target.value })} />
          </div>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Stop time *</label>
            <input className='form-control' type='datetime-local' value={form.stopTime} onChange={e => set({ stopTime: e.target.value })} />
          </div>
        </div>
        <div className='row'>
          <div className='col-md-6 mb-3'>
            <label className='form-label'>Deal stock</label>
            <input className='form-control' type='number' min='0' step='1' value={form.stock} onChange={e => set({ stock: e.target.value })} />
            <div className='form-text'>0 = uncapped</div>
          </div>
          {isEdit && (
            <div className='col-md-6 mb-3 d-flex align-items-center'>
              <div className='form-check mt-4'>
                <input
                  className='form-check-input'
                  type='checkbox'
                  id='deal-enabled'
                  checked={form.enabled}
                  onChange={e => set({ enabled: e.target.checked })}
                />
                <label className='form-check-label' htmlFor='deal-enabled'>
                  Enabled
                </label>
              </div>
            </div>
          )}
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/promotion/deal')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default DealForm;
