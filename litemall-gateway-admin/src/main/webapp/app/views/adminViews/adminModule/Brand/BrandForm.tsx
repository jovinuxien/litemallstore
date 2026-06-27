import { IBrand } from 'app/shared/model/admin/catalog.model';
import { useCreateBrandMutation, useReadBrandQuery, useUpdateBrandMutation } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a brand. With an :id route param it loads the existing row and
// PUTs an update; otherwise it creates. Validation mirrors the server
// (name + desc + floorPrice required). All calls run as authenticated admin.

const empty: IBrand = { name: '', desc: '', picUrl: '', floorPrice: 0, sortOrder: 0 };

const BrandForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadBrandQuery(id as string, { skip: !isEdit });
  const [createBrand, { isLoading: creating }] = useCreateBrandMutation();
  const [updateBrand, { isLoading: updating }] = useUpdateBrandMutation();

  const [form, setForm] = React.useState<IBrand>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IBrand>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim() || !form.desc?.trim()) {
      setError('Name and description are required.');
      return;
    }
    if (form.floorPrice == null || Number.isNaN(Number(form.floorPrice))) {
      setError('Floor price is required.');
      return;
    }
    const body: IBrand = { ...form, floorPrice: Number(form.floorPrice), sortOrder: Number(form.sortOrder ?? 0) };
    const res = await (isEdit ? updateBrand(body) : createBrand(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/brand');
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
      <h5 className='mb-3'>{isEdit ? `Edit brand #${id}` : 'New brand'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Name *</label>
          <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Description *</label>
          <textarea className='form-control' rows={3} value={form.desc ?? ''} onChange={e => set({ desc: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Image URL</label>
          <input className='form-control' value={form.picUrl ?? ''} onChange={e => set({ picUrl: e.target.value })} />
          {form.picUrl && <img src={form.picUrl} alt='' className='cell-thumb mt-2' />}
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Floor price *</label>
            <input
              className='form-control'
              type='number'
              step='0.01'
              value={form.floorPrice ?? 0}
              onChange={e => set({ floorPrice: e.target.value === '' ? undefined : Number(e.target.value) })}
            />
          </div>
          <div className='col mb-3'>
            <label className='form-label'>Sort order</label>
            <input className='form-control' type='number' value={form.sortOrder ?? 0} onChange={e => set({ sortOrder: Number(e.target.value) })} />
          </div>
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/brand')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default BrandForm;
