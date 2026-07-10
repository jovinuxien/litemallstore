import { IRole } from 'app/shared/model/admin/promotion-system.model';
import { useCreateRoleMutation, useListRolesQuery, useUpdateRoleMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit a role. Server requires a non-empty name. The edit form
// hydrates from the role list (small dataset).

const empty: IRole = { name: '', desc: '', enabled: true };

const RoleForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: roles, isLoading: loading } = useListRolesQuery({ page: 1, limit: 200, sort: 'add_time', order: 'desc' }, { skip: !isEdit });
  const [createRole, { isLoading: creating }] = useCreateRoleMutation();
  const [updateRole, { isLoading: updating }] = useUpdateRoleMutation();

  const [form, setForm] = React.useState<IRole>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && roles?.list) {
      const found = roles.list.find(r => String(r.id) === id);
      if (found) setForm(found);
    }
  }, [isEdit, roles, id]);

  const set = (patch: Partial<IRole>) => setForm(prev => ({ ...prev, ...patch }));

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.name?.trim()) {
      setError('Name is required.');
      return;
    }
    const res = await (isEdit ? updateRole(form) : createRole(form));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/sys/role');
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
      <h5 className='mb-3'>{isEdit ? `Edit role #${id}` : 'New role'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Name *</label>
          <input className='form-control' value={form.name ?? ''} onChange={e => set({ name: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Description</label>
          <input className='form-control' value={form.desc ?? ''} onChange={e => set({ desc: e.target.value })} />
        </div>
        <div className='mb-3'>
          <div className='form-check'>
            <input className='form-check-input' type='checkbox' id='role-enabled' checked={form.enabled !== false} onChange={e => set({ enabled: e.target.checked })} />
            <label className='form-check-label' htmlFor='role-enabled'>
              Enabled
            </label>
          </div>
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/sys/role')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default RoleForm;
