import { IAd } from 'app/shared/model/admin/promotion-system.model';
import { useCreateAdMutation, useReadAdQuery, useUpdateAdMutation } from 'app/shared/reducers/private/services/adminPromotionApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit an advertisement. Server requires a non-empty name and content.

const empty: IAd = { name: '', link: '', url: '', content: '', position: 1, enabled: true };

const AdForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadAdQuery(id as string, { skip: !isEdit });
  const [createAd, { isLoading: creating }] = useCreateAdMutation();
  const [updateAd, { isLoading: updating }] = useUpdateAdMutation();

  const [form, setForm] = React.useState<IAd>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IAd>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Name is required.');
      return;
    }
    if (!form.content?.trim()) {
      setError('Content is required.');
      return;
    }
    const body: IAd = { ...form, position: Number(form.position ?? 1) };
    const res = await (isEdit ? updateAd(body) : createAd(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/promotion/ad');
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
      <h5 className='mb-3'>{isEdit ? `Edit ad #${id}` : 'New ad'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Name *</label>
          <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Image URL</label>
          <input className='form-control' value={form.url ?? ''} onChange={e => set({ url: e.target.value })} />
          {form.url && <img src={form.url} alt='preview' className='mt-2' style={{ maxHeight: 80 }} />}
        </div>
        <div className='mb-3'>
          <label className='form-label'>Link (target URL)</label>
          <input className='form-control' value={form.link ?? ''} onChange={e => set({ link: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Content *</label>
          <textarea className='form-control' rows={2} value={form.content ?? ''} onChange={e => set({ content: e.target.value })} />
        </div>
        <div className='row align-items-center'>
          <div className='col mb-3'>
            <label className='form-label'>Position</label>
            <input className='form-control' type='number' value={form.position ?? 1} onChange={e => set({ position: Number(e.target.value) })} />
          </div>
          <div className='col-auto mb-3'>
            <div className='form-check'>
              <input className='form-check-input' type='checkbox' id='ad-enabled' checked={Boolean(form.enabled)} onChange={e => set({ enabled: e.target.checked })} />
              <label className='form-check-label' htmlFor='ad-enabled'>
                Enabled
              </label>
            </div>
          </div>
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/promotion/ad')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default AdForm;
