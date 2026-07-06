import { useListAddressesQuery } from 'app/shared/reducers/private/services/adminUsersApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Admin address book across all customers — userId/consignee filters + paging,
// through the gateway as an authenticated admin
// (adminUsersApi → /srv/private/admin/address). Mirrors the BrandList look.

const fmtTime = (value?: string) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const AddressList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [userIdInput, setUserIdInput] = React.useState('');
  const [nameInput, setNameInput] = React.useState('');
  const [filters, setFilters] = React.useState<{ userId?: string; name?: string }>({});

  const { data, isLoading, isFetching, isError, error } = useListAddressesQuery({ page, limit, ...filters });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setFilters({ userId: userIdInput.trim() || undefined, name: nameInput.trim() || undefined });
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 140 }}
          placeholder='User ID'
          value={userIdInput}
          onChange={e => setUserIdInput(e.target.value.replace(/[^\d]/g, ''))}
        />
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Consignee name'
          value={nameInput}
          onChange={e => setNameInput(e.target.value)}
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

      {isError && <div className='alert alert-danger'>Failed to load addresses{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>ID</th>
            <th>User</th>
            <th>Consignee</th>
            <th>Phone</th>
            <th>Address</th>
            <th>Postal code</th>
            <th>Default</th>
            <th>Added</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={8} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={8} className='text-center text-muted py-5'>
                No addresses found.
              </td>
            </tr>
          ) : (
            list.map(address => (
              <tr key={address.id}>
                <td>{address.id}</td>
                <td>#{address.userId}</td>
                <td>{address.name || '—'}</td>
                <td>{address.tel || '—'}</td>
                <td className='text-muted'>
                  {[address.province, address.city, address.county, address.addressDetail].filter(Boolean).join(' ') || '—'}
                </td>
                <td>{address.postalCode || '—'}</td>
                <td>{address.isDefault ? <Tag tag='success'>Default</Tag> : <span className='text-muted'>—</span>}</td>
                <td className='text-muted small'>{fmtTime(address.addTime)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default AddressList;
