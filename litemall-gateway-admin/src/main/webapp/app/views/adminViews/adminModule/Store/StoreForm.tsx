import { IStore, useCreateStoreMutation, useReadStoreQuery, useUpdateStoreMutation } from 'app/shared/reducers/private/services/adminStoreApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a physical pickup store. With an :id route param it loads the
// existing row and POSTs an update; otherwise it creates. Name, phone and
// address are required; latitude/longitude are optional numeric inputs.
// Mirrors the BrandForm pattern (adminStoreApi → /srv/private/admin/store).

const empty: IStore = {
  name: '',
  intro: '',
  phone: '',
  address: '',
  detailedAddress: '',
  logo: '',
  latitude: undefined,
  longitude: undefined,
  businessHours: '',
  isShow: true,
};

const StoreForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadStoreQuery(id as string, { skip: !isEdit });
  const [createStore, { isLoading: creating }] = useCreateStoreMutation();
  const [updateStore, { isLoading: updating }] = useUpdateStoreMutation();

  const [form, setForm] = React.useState<IStore>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IStore>) => setForm(prev => ({ ...prev, ...patch }));

  const numOrUndef = (v: string): number | undefined => (v === '' ? undefined : Number(v));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Store name is required.');
      return;
    }
    if (!form.phone?.trim()) {
      setError('Phone is required.');
      return;
    }
    if (!form.address?.trim()) {
      setError('Address is required.');
      return;
    }
    if ((form.latitude != null && Number.isNaN(form.latitude)) || (form.longitude != null && Number.isNaN(form.longitude))) {
      setError('Latitude and longitude must be numbers.');
      return;
    }
    const body: IStore = { ...form, isShow: Boolean(form.isShow) };
    const res = await (isEdit ? updateStore(body) : createStore(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/store');
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
      <h5 className='mb-3'>{isEdit ? `Edit store #${id}` : 'New store'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Name *</label>
          <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Introduction</label>
          <textarea className='form-control' rows={3} value={form.intro ?? ''} onChange={e => set({ intro: e.target.value })} />
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Phone *</label>
            <input className='form-control' value={form.phone ?? ''} onChange={e => set({ phone: e.target.value })} />
          </div>
          <div className='col mb-3'>
            <label className='form-label'>Business hours</label>
            <input
              className='form-control'
              placeholder='e.g. 09:00 - 21:00'
              value={form.businessHours ?? ''}
              onChange={e => set({ businessHours: e.target.value })}
            />
          </div>
        </div>
        <div className='mb-3'>
          <label className='form-label'>Address *</label>
          <input className='form-control' value={form.address ?? ''} onChange={e => set({ address: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Detailed address</label>
          <input
            className='form-control'
            placeholder='Building / floor / unit'
            value={form.detailedAddress ?? ''}
            onChange={e => set({ detailedAddress: e.target.value })}
          />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Logo URL</label>
          <input className='form-control' value={form.logo ?? ''} onChange={e => set({ logo: e.target.value })} />
          {form.logo && <img src={form.logo} alt='' className='cell-thumb mt-2' />}
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Latitude</label>
            <input
              className='form-control'
              type='number'
              step='any'
              min={-90}
              max={90}
              value={form.latitude ?? ''}
              onChange={e => set({ latitude: numOrUndef(e.target.value) })}
            />
          </div>
          <div className='col mb-3'>
            <label className='form-label'>Longitude</label>
            <input
              className='form-control'
              type='number'
              step='any'
              min={-180}
              max={180}
              value={form.longitude ?? ''}
              onChange={e => set({ longitude: numOrUndef(e.target.value) })}
            />
          </div>
        </div>
        <div className='form-check mb-3'>
          <input
            className='form-check-input'
            type='checkbox'
            id='store-is-show'
            checked={Boolean(form.isShow)}
            onChange={e => set({ isShow: e.target.checked })}
          />
          <label className='form-check-label' htmlFor='store-is-show'>
            Visible to customers
          </label>
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/store')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default StoreForm;
