import { IKeyword } from 'app/shared/model/admin/catalog.model';
import { useCreateKeywordMutation, useReadKeywordQuery, useUpdateKeywordMutation } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a search keyword. Server requires a non-empty keyword.

const empty: IKeyword = { keyword: '', url: '', isHot: false, isDefault: false, sortOrder: 0 };

const KeywordForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadKeywordQuery(id as string, { skip: !isEdit });
  const [createKeyword, { isLoading: creating }] = useCreateKeywordMutation();
  const [updateKeyword, { isLoading: updating }] = useUpdateKeywordMutation();

  const [form, setForm] = React.useState<IKeyword>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IKeyword>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.keyword?.trim()) {
      setError('Keyword is required.');
      return;
    }
    const body: IKeyword = { ...form, sortOrder: Number(form.sortOrder ?? 0) };
    const res = await (isEdit ? updateKeyword(body) : createKeyword(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/keyword');
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
      <h5 className='mb-3'>{isEdit ? `Edit keyword #${id}` : 'New keyword'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Keyword *</label>
          <input className='form-control' value={form.keyword ?? ''} onChange={e => set({ keyword: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>URL</label>
          <input className='form-control' value={form.url ?? ''} onChange={e => set({ url: e.target.value })} />
        </div>
        <div className='row align-items-center'>
          <div className='col-auto mb-3'>
            <div className='form-check'>
              <input className='form-check-input' type='checkbox' id='kw-hot' checked={Boolean(form.isHot)} onChange={e => set({ isHot: e.target.checked })} />
              <label className='form-check-label' htmlFor='kw-hot'>
                Hot
              </label>
            </div>
          </div>
          <div className='col-auto mb-3'>
            <div className='form-check'>
              <input className='form-check-input' type='checkbox' id='kw-default' checked={Boolean(form.isDefault)} onChange={e => set({ isDefault: e.target.checked })} />
              <label className='form-check-label' htmlFor='kw-default'>
                Default
              </label>
            </div>
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
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/keyword')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default KeywordForm;
