import { ICategory } from 'app/shared/model/admin/catalog.model';
import {
  useCategoryL1Query,
  useCreateCategoryMutation,
  useReadCategoryQuery,
  useUpdateCategoryMutation,
} from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';

// Create / edit a category (LitemallCategory). Level L1 or L2; an L2 must carry
// a parent (pid), chosen from the L1 options (/category/l1). Creating via the
// list's "+ Sub" shortcut pre-selects level L2 + the parent id from ?pid=.

const CategoryForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const presetPid = searchParams.get('pid');

  const { data: existing, isLoading: loading } = useReadCategoryQuery(id as string, { skip: !isEdit });
  const { data: l1Options = [] } = useCategoryL1Query();
  const [createCategory, { isLoading: creating }] = useCreateCategoryMutation();
  const [updateCategory, { isLoading: updating }] = useUpdateCategoryMutation();

  const initial: ICategory = React.useMemo(
    () => ({
      name: '',
      level: presetPid ? 'L2' : 'L1',
      pid: presetPid ? Number(presetPid) : undefined,
      keywords: '',
      desc: '',
      iconUrl: '',
      picUrl: '',
      sortOrder: 0,
    }),
    [presetPid]
  );

  const [form, setForm] = React.useState<ICategory>(initial);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  React.useEffect(() => {
    if (!isEdit) setForm(initial);
  }, [isEdit, initial]);

  const set = (patch: Partial<ICategory>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Name is required.');
      return;
    }
    if (form.level === 'L2' && form.pid == null) {
      setError('A level-2 category requires a parent category.');
      return;
    }
    const body: ICategory = {
      ...form,
      pid: form.level === 'L2' ? Number(form.pid) : 0,
      sortOrder: Number(form.sortOrder ?? 0),
    };
    const res = await (isEdit ? updateCategory(body) : createCategory(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/category');
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
      <h5 className='mb-3'>{isEdit ? `Edit category #${id}` : 'New category'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }}>
        <div className='mb-3'>
          <label className='form-label'>Name *</label>
          <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Level</label>
            <select
              className='form-select'
              value={form.level ?? 'L1'}
              onChange={e => set({ level: e.target.value as 'L1' | 'L2', pid: e.target.value === 'L1' ? undefined : form.pid })}
            >
              <option value='L1'>L1 (top level)</option>
              <option value='L2'>L2 (subcategory)</option>
            </select>
          </div>
          {form.level === 'L2' && (
            <div className='col mb-3'>
              <label className='form-label'>Parent category *</label>
              <select className='form-select' value={form.pid ?? ''} onChange={e => set({ pid: e.target.value === '' ? undefined : Number(e.target.value) })}>
                <option value=''>— select parent —</option>
                {l1Options
                  .filter(o => o.value !== form.id)
                  .map(o => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
              </select>
            </div>
          )}
        </div>
        <div className='mb-3'>
          <label className='form-label'>Keywords</label>
          <input className='form-control' value={form.keywords ?? ''} onChange={e => set({ keywords: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Description</label>
          <textarea className='form-control' rows={2} value={form.desc ?? ''} onChange={e => set({ desc: e.target.value })} />
        </div>
        <div className='row'>
          <div className='col mb-3'>
            <label className='form-label'>Icon URL</label>
            <input className='form-control' value={form.iconUrl ?? ''} onChange={e => set({ iconUrl: e.target.value })} />
            {form.iconUrl && <img src={form.iconUrl} alt='' className='cell-thumb mt-2' />}
          </div>
          <div className='col mb-3'>
            <label className='form-label'>Picture URL</label>
            <input className='form-control' value={form.picUrl ?? ''} onChange={e => set({ picUrl: e.target.value })} />
            {form.picUrl && <img src={form.picUrl} alt='' className='cell-thumb mt-2' />}
          </div>
        </div>
        <div className='mb-3' style={{ maxWidth: 160 }}>
          <label className='form-label'>Sort order</label>
          <input className='form-control' type='number' value={form.sortOrder ?? 0} onChange={e => set({ sortOrder: Number(e.target.value) })} />
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/category')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default CategoryForm;
