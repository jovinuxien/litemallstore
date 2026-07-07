import { INotice } from 'app/shared/model/admin/promotion-system.model';
import { useCreateNoticeMutation, useListNoticesQuery, useUpdateNoticeMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a notice. Server requires a non-empty title; a notice that has
// already been read by an admin can no longer be edited (server enforces).
// There is no /notice/read that returns the plain notice, so the edit form
// hydrates from the notice list.

const empty: INotice = { title: '', content: '' };

const NoticeForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: notices, isLoading: loading } = useListNoticesQuery({ page: 1, limit: 200, sort: 'add_time', order: 'desc' }, { skip: !isEdit });
  const [createNotice, { isLoading: creating }] = useCreateNoticeMutation();
  const [updateNotice, { isLoading: updating }] = useUpdateNoticeMutation();

  const [form, setForm] = React.useState<INotice>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && notices?.list) {
      const found = notices.list.find(n => String(n.id) === id);
      if (found) setForm(found);
    }
  }, [isEdit, notices, id]);

  const set = (patch: Partial<INotice>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.title?.trim()) {
      setError('Title is required.');
      return;
    }
    const res = await (isEdit ? updateNotice(form) : createNotice(form));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/sys/notice');
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
      <h5 className='mb-3'>{isEdit ? `Edit notice #${id}` : 'New notice'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }}>
        <div className='mb-3'>
          <label className='form-label'>Title *</label>
          <input className='form-control' value={form.title ?? ''} onChange={e => set({ title: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Content</label>
          <textarea className='form-control' rows={6} value={form.content ?? ''} onChange={e => set({ content: e.target.value })} />
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/sys/notice')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default NoticeForm;
