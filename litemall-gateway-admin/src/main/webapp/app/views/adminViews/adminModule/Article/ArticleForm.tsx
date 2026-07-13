import {
  IArticle,
  useCreateArticleMutation,
  useListArticleCategoriesQuery,
  useReadArticleQuery,
  useUpdateArticleMutation,
} from 'app/shared/reducers/private/services/adminContentApi';
import { useUploadStorageMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit an article. With an :id route param it loads the raw admin row
// (content included) and POSTs an update; otherwise it creates. The cover
// image can be uploaded through the storage vertical (multipart POST
// /srv/private/admin/storage/create — reuses adminSysApi.uploadStorage) or
// pasted as a URL. Content is raw HTML in a textarea — the SERVER sanitizes it
// on save (jsoup clean-and-store), so disallowed markup is silently stripped,
// never rejected. Contract: handoff-content-endpoints.md §1.

const empty: IArticle = {
  categoryId: 0,
  title: '',
  summary: '',
  picUrl: '',
  content: '',
  status: 'published',
  isHot: false,
  isBanner: false,
  goodsId: 0,
};

const ArticleForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadArticleQuery(id as string, { skip: !isEdit });
  const { data: categories = [] } = useListArticleCategoriesQuery();
  const [createArticle, { isLoading: creating }] = useCreateArticleMutation();
  const [updateArticle, { isLoading: updating }] = useUpdateArticleMutation();
  const [uploadStorage, { isLoading: uploading }] = useUploadStorageMutation();

  const [form, setForm] = React.useState<IArticle>(empty);
  const [error, setError] = React.useState<string | null>(null);
  const fileRef = React.useRef<HTMLInputElement>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IArticle>) => setForm(prev => ({ ...prev, ...patch }));

  const onUploadPic = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);
    const fd = new FormData();
    fd.append('file', file);
    const res = await uploadStorage(fd);
    if (fileRef.current) fileRef.current.value = '';
    if (!('data' in res)) {
      setError('Image upload failed.');
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) {
      setError(msg);
      return;
    }
    const url = res.data?.data?.url;
    if (url) set({ picUrl: url });
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.title?.trim()) {
      setError('Title is required.');
      return;
    }
    if (!form.content?.trim()) {
      setError('Content is required.');
      return;
    }
    const body: IArticle = {
      ...form,
      title: form.title.trim(),
      categoryId: Number(form.categoryId ?? 0),
      goodsId: Number(form.goodsId ?? 0) || 0,
    };
    // categoryName/viewCount/timestamps are server-derived read fields.
    delete body.categoryName;
    delete body.viewCount;
    delete body.addTime;
    delete body.updateTime;
    const res = await (isEdit ? updateArticle(body) : createArticle(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/article');
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
      <h5 className='mb-3'>{isEdit ? `Edit article #${id}` : 'New article'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 760 }}>
        <div className='row'>
          <div className='col-8 mb-3'>
            <label className='form-label'>Title *</label>
            <input className='form-control' value={form.title ?? ''} onChange={e => set({ title: e.target.value })} />
          </div>
          <div className='col-4 mb-3'>
            <label className='form-label'>Category</label>
            <select
              className='form-select'
              value={form.categoryId ?? 0}
              onChange={e => set({ categoryId: Number(e.target.value) })}
              aria-label='Category'
            >
              <option value={0}>Uncategorized</option>
              {categories.map(c => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className='mb-3'>
          <label className='form-label'>Summary</label>
          <textarea className='form-control' rows={2} maxLength={511} value={form.summary ?? ''} onChange={e => set({ summary: e.target.value })} />
        </div>

        <div className='mb-3'>
          <label className='form-label'>Cover image</label>
          <div className='d-flex gap-2'>
            <input
              className='form-control'
              placeholder='Image URL (or upload)'
              value={form.picUrl ?? ''}
              onChange={e => set({ picUrl: e.target.value })}
            />
            <label className='btn btn-outline-secondary mb-0' style={{ whiteSpace: 'nowrap' }}>
              {uploading ? 'Uploading…' : 'Upload'}
              <input ref={fileRef} type='file' accept='image/*' hidden onChange={onUploadPic} disabled={uploading} />
            </label>
          </div>
          {form.picUrl && <img src={form.picUrl} alt='' className='mt-2' style={{ maxHeight: 120, maxWidth: '100%', objectFit: 'contain' }} />}
        </div>

        <div className='mb-3'>
          <label className='form-label'>Content *</label>
          <textarea
            className='form-control font-monospace'
            rows={12}
            value={form.content ?? ''}
            onChange={e => set({ content: e.target.value })}
          />
          <div className='form-text'>
            Rich HTML is allowed. The server sanitizes it on save (clean-and-store) — disallowed tags and attributes are stripped silently, never
            rejected.
          </div>
        </div>

        <div className='row'>
          <div className='col-auto mb-3'>
            <label className='form-label d-block'>Status</label>
            <div className='btn-group' role='group' aria-label='Status'>
              <button
                type='button'
                className={`btn btn-sm ${form.status === 'published' ? 'btn-success' : 'btn-outline-success'}`}
                onClick={() => set({ status: 'published' })}
              >
                Published
              </button>
              <button
                type='button'
                className={`btn btn-sm ${form.status === 'hidden' ? 'btn-secondary' : 'btn-outline-secondary'}`}
                onClick={() => set({ status: 'hidden' })}
              >
                Hidden
              </button>
            </div>
          </div>
          <div className='col-auto mb-3'>
            <label className='form-label d-block'>Flags</label>
            <div className='form-check form-check-inline'>
              <input
                id='article-is-hot'
                className='form-check-input'
                type='checkbox'
                checked={Boolean(form.isHot)}
                onChange={e => set({ isHot: e.target.checked })}
              />
              <label className='form-check-label' htmlFor='article-is-hot'>
                Hot
              </label>
            </div>
            <div className='form-check form-check-inline'>
              <input
                id='article-is-banner'
                className='form-check-input'
                type='checkbox'
                checked={Boolean(form.isBanner)}
                onChange={e => set({ isBanner: e.target.checked })}
              />
              <label className='form-check-label' htmlFor='article-is-banner'>
                Banner
              </label>
            </div>
          </div>
          <div className='col-auto mb-3'>
            <label className='form-label'>Related goods ID</label>
            <input
              className='form-control'
              style={{ width: 160 }}
              type='number'
              min={0}
              value={form.goodsId ?? 0}
              onChange={e => set({ goodsId: e.target.value === '' ? 0 : Number(e.target.value) })}
            />
            <div className='form-text'>0 = none</div>
          </div>
        </div>

        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/article')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default ArticleForm;
