import { IBrand } from 'app/shared/model/admin/catalog.model';
import { useCreateBrandMutation, useReadBrandQuery, useUpdateBrandMutation } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { isDisplayEnabled, isProviderRow, kindLabel, sourceLabel } from 'app/views/adminViews/adminModule/Brand/brandFormat';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a brand. With an :id route param it loads the existing row and
// PUTs an update; otherwise it creates. Validation mirrors the server
// (name + desc + floorPrice required) — except on provider-sourced rows
// (cj-supplier etc.), where renaming is the curation act and only the name is
// required. All calls run as authenticated admin.

// No displayEnabled here: the backend rejects unknown JSON fields (errno 402,
// core Jackson strictness), so V60 fields ride an outbound body only when the
// loaded row carried them or the admin touched the visibility checkbox. The
// checkbox still renders checked (absent ⇒ visible) and the V60 backend owns
// defaulting new manual rows to enabled.
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

  const provider = isEdit && isProviderRow(form);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Name is required.');
      return;
    }
    if (!provider) {
      if (!form.desc?.trim()) {
        setError('Name and description are required.');
        return;
      }
      if (form.floorPrice == null || Number.isNaN(Number(form.floorPrice))) {
        setError('Floor price is required.');
        return;
      }
    }
    const { goodsCount, ...row } = form;
    const body: IBrand = { ...row, floorPrice: Number(form.floorPrice ?? 0), sortOrder: Number(form.sortOrder ?? 0) };
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
        {provider && (
          <div className='mb-3 p-2 bg-light border rounded small'>
            <Tag tag={kindLabel(form.kind).tone}>{kindLabel(form.kind).label}</Tag>{' '}
            <span className='text-muted'>
              Source: {sourceLabel(form.source)}
              {form.externalId ? ` · External id: ${form.externalId}` : ''}
            </span>
            <div className='text-muted mt-1'>
              Provider-created row — rename it to a customer-worthy store name, then enable visibility below.
            </div>
          </div>
        )}
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
        <div className='form-check mb-3'>
          <input
            id='brand-display-enabled'
            className='form-check-input'
            type='checkbox'
            checked={isDisplayEnabled(form.displayEnabled)}
            onChange={e => set({ displayEnabled: e.target.checked })}
          />
          <label className='form-check-label' htmlFor='brand-display-enabled'>
            Visible on storefront
          </label>
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
