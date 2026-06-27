import { IIssue } from 'app/shared/model/admin/catalog.model';
import { useCreateIssueMutation, useReadIssueQuery, useUpdateIssueMutation } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a FAQ entry. Server requires non-empty question + answer.

const empty: IIssue = { question: '', answer: '' };

const IssueForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadIssueQuery(id as string, { skip: !isEdit });
  const [createIssue, { isLoading: creating }] = useCreateIssueMutation();
  const [updateIssue, { isLoading: updating }] = useUpdateIssueMutation();

  const [form, setForm] = React.useState<IIssue>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm(existing);
  }, [isEdit, existing]);

  const set = (patch: Partial<IIssue>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.question?.trim() || !form.answer?.trim()) {
      setError('Question and answer are required.');
      return;
    }
    const res = await (isEdit ? updateIssue(form) : createIssue(form));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/mall/issue');
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
      <h5 className='mb-3'>{isEdit ? `Edit issue #${id}` : 'New issue'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 720 }}>
        <div className='mb-3'>
          <label className='form-label'>Question *</label>
          <input className='form-control' value={form.question ?? ''} onChange={e => set({ question: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Answer *</label>
          <textarea className='form-control' rows={5} value={form.answer ?? ''} onChange={e => set({ answer: e.target.value })} />
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/mall/issue')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default IssueForm;
