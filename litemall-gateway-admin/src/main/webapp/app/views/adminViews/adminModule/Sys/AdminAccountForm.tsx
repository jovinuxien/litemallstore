import { IAdmin } from 'app/shared/model/admin/promotion-system.model';
import { useCreateAdminMutation, useReadAdminQuery, useRoleOptionsQuery, useUpdateAdminMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { useNavigate, useParams } from 'react-router-dom';

// Create / edit an administrator account. A password (min 6 chars) is required
// on create only; the edit endpoint never changes the password server-side.

const empty: IAdmin = { username: '', password: '', avatar: '', roleIds: [] };

const AdminAccountForm: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const isEdit = Boolean(id);
  const navigate = useNavigate();

  const { data: existing, isLoading: loading } = useReadAdminQuery(id as string, { skip: !isEdit });
  const { data: roleOptions = [] } = useRoleOptionsQuery();
  const [createAdmin, { isLoading: creating }] = useCreateAdminMutation();
  const [updateAdmin, { isLoading: updating }] = useUpdateAdminMutation();

  const [form, setForm] = React.useState<IAdmin>(empty);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    if (isEdit && existing && existing.id != null) setForm({ ...existing, password: '' });
  }, [isEdit, existing]);

  const set = (patch: Partial<IAdmin>) => setForm(prev => ({ ...prev, ...patch }));

  const toggleRole = (roleId: number) => {
    const current = form.roleIds ?? [];
    set({ roleIds: current.includes(roleId) ? current.filter(r => r !== roleId) : [...current, roleId] });
  };

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!form.username?.trim()) {
      setError('Username is required.');
      return;
    }
    if (!isEdit && (!form.password || form.password.length < 6)) {
      setError('Password must be at least 6 characters.');
      return;
    }
    // On edit the password field is ignored server-side; don't send it.
    const body: IAdmin = isEdit ? { ...form, password: undefined } : form;
    const res = await (isEdit ? updateAdmin(body) : createAdmin(body));
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setError(msg);
      return;
    }
    navigate('/admin/sys/admin');
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
      <h5 className='mb-3'>{isEdit ? `Edit administrator #${id}` : 'New administrator'}</h5>
      {error && <div className='alert alert-danger'>{error}</div>}
      <form onSubmit={onSubmit} style={{ maxWidth: 640 }}>
        <div className='mb-3'>
          <label className='form-label'>Username *</label>
          <input className='form-control' value={form.username ?? ''} onChange={e => set({ username: e.target.value })} autoComplete='off' />
        </div>
        {!isEdit && (
          <div className='mb-3'>
            <label className='form-label'>Password * (min 6 chars)</label>
            <input className='form-control' type='password' value={form.password ?? ''} onChange={e => set({ password: e.target.value })} autoComplete='new-password' />
          </div>
        )}
        {isEdit && <div className='alert alert-info py-2 small'>Passwords cannot be changed from this screen.</div>}
        <div className='mb-3'>
          <label className='form-label'>Avatar URL</label>
          <input className='form-control' value={form.avatar ?? ''} onChange={e => set({ avatar: e.target.value })} />
        </div>
        <div className='mb-3'>
          <label className='form-label'>Roles</label>
          {roleOptions.length === 0 ? (
            <div className='form-text'>No roles defined yet.</div>
          ) : (
            <div>
              {roleOptions.map(opt => (
                <div className='form-check' key={opt.value}>
                  <input
                    className='form-check-input'
                    type='checkbox'
                    id={`role-${opt.value}`}
                    checked={(form.roleIds ?? []).includes(opt.value)}
                    onChange={() => toggleRole(opt.value)}
                  />
                  <label className='form-check-label' htmlFor={`role-${opt.value}`}>
                    {opt.label}
                  </label>
                </div>
              ))}
            </div>
          )}
        </div>
        <div className='mt-3'>
          <button className='btn btn-primary me-2' type='submit' disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button className='btn btn-outline-secondary' type='button' onClick={() => navigate('/admin/sys/admin')}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
};

export default AdminAccountForm;
