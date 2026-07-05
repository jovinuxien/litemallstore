import { IAdminUser, useListUsersQuery, useUpdateUserMutation } from 'app/shared/reducers/private/services/adminUsersApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Admin customer list — username/mobile filters, paging, level/status pills and
// an enable/disable toggle, through the gateway as an authenticated admin
// (adminUsersApi → /srv/private/admin/user). Mirrors the BrandList look.

const GENDERS = ['—', 'Male', 'Female'];
const LEVELS: { label: string; tag: 'info' | 'warning' | 'success' }[] = [
  { label: 'Normal', tag: 'info' },
  { label: 'VIP', tag: 'warning' },
  { label: 'Gold', tag: 'success' },
];

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const UserList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [usernameInput, setUsernameInput] = React.useState('');
  const [mobileInput, setMobileInput] = React.useState('');
  const [filters, setFilters] = React.useState({ username: '', mobile: '' });

  const { data, isLoading, isFetching, isError, error } = useListUsersQuery({ page, limit, sort: 'add_time', order: 'desc', ...filters });
  const [updateUser, { isLoading: updating }] = useUpdateUserMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ username: usernameInput.trim(), mobile: mobileInput.trim() });
  };

  const onToggleStatus = async (user: IAdminUser) => {
    const disable = (user.status ?? 0) === 0;
    if (!window.confirm(`${disable ? 'Disable' : 'Enable'} user "${user.username ?? user.id}"?`)) return;
    setActionError(null);
    const res = await updateUser({ id: user.id, status: disable ? 1 : 0 });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Username'
          value={usernameInput}
          onChange={e => setUsernameInput(e.target.value)}
        />
        <input
          className='form-control filter-item'
          style={{ width: 180 }}
          placeholder='Mobile'
          value={mobileInput}
          onChange={e => setMobileInput(e.target.value)}
        />
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => {
            setPage(1);
            setLimit(Number(e.target.value));
          }}
          aria-label='Page size'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load users{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Avatar</th>
            <th>Username</th>
            <th>Nickname</th>
            <th>Mobile</th>
            <th>Gender</th>
            <th>Level</th>
            <th>Status</th>
            <th>Last login</th>
            <th>Registered</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={10} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={10} className='text-center text-muted py-5'>
                No users found.
              </td>
            </tr>
          ) : (
            list.map(user => {
              const level = LEVELS[user.userLevel ?? 0] ?? LEVELS[0];
              const disabled = (user.status ?? 0) !== 0;
              return (
                <tr key={user.id}>
                  <td style={{ width: 56 }}>
                    {user.avatar ? <img src={user.avatar} alt={user.username ?? ''} className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />}
                  </td>
                  <td>{user.username || `#${user.id}`}</td>
                  <td className='text-muted'>{user.nickname || '—'}</td>
                  <td>{user.mobile || '—'}</td>
                  <td>{GENDERS[user.gender ?? 0] ?? '—'}</td>
                  <td>
                    <Tag tag={level.tag}>{level.label}</Tag>
                  </td>
                  <td>{disabled ? <Tag tag='danger'>Disabled</Tag> : <Tag tag='success'>Active</Tag>}</td>
                  <td className='text-muted small'>{fmtTime(user.lastLoginTime)}</td>
                  <td className='text-muted small'>{fmtTime(user.addTime)}</td>
                  <td className='text-end'>
                    <button
                      className={`btn btn-sm ${disabled ? 'btn-outline-success' : 'btn-outline-danger'}`}
                      disabled={updating}
                      onClick={() => onToggleStatus(user)}
                    >
                      {disabled ? 'Enable' : 'Disable'}
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default UserList;
