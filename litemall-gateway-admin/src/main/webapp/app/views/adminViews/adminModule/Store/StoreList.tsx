import {
  IStore,
  useDeleteStoreMutation,
  useListStoresQuery,
  useUpdateStoreMutation,
} from 'app/shared/reducers/private/services/adminStoreApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Physical pickup-store management — list with name filter and paging, an
// inline show/hide toggle (POSTs a full update), plus create/edit/delete.
// All through the gateway as an authenticated admin
// (adminStoreApi → /srv/private/admin/store). Mirrors the BrandList look.

const StoreList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [nameInput, setNameInput] = React.useState('');
  const [name, setName] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListStoresQuery({ page, limit, name });
  const [updateStore, { isLoading: updating }] = useUpdateStoreMutation();
  const [deleteStore, { isLoading: deleting }] = useDeleteStoreMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setName(nameInput.trim());
  };

  const onToggleShow = async (store: IStore) => {
    setActionError(null);
    const res = await updateStore({ ...store, isShow: !store.isShow });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const onDelete = async (store: IStore) => {
    if (!window.confirm(`Delete store "${store.name ?? store.id}"?`)) return;
    setActionError(null);
    const res = await deleteStore({ id: store.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Store name'
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
        <Link className='btn btn-success filter-item' to='/admin/mall/store/create'>
          + New store
        </Link>
        <Link className='btn btn-outline-primary filter-item' to='/admin/mall/writeoff'>
          Write-off console
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load stores{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Logo</th>
            <th>Name</th>
            <th>Phone</th>
            <th>Address</th>
            <th>Business hours</th>
            <th className='text-center'>Visible</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-5'>
                No stores found.
              </td>
            </tr>
          ) : (
            list.map(store => (
              <tr key={store.id}>
                <td style={{ width: 56 }}>
                  {store.logo ? <img src={store.logo} alt={store.name ?? ''} className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />}
                </td>
                <td>
                  <Link to={`/admin/mall/store/${store.id}`}>{store.name || `#${store.id}`}</Link>
                  {store.intro && <div className='text-muted small'>{store.intro}</div>}
                </td>
                <td>{store.phone ?? '—'}</td>
                <td className='text-muted small'>
                  {store.address ?? '—'}
                  {store.detailedAddress && <div>{store.detailedAddress}</div>}
                </td>
                <td>{store.businessHours ?? '—'}</td>
                <td className='text-center'>
                  <div className='form-check form-switch d-inline-block'>
                    <input
                      className='form-check-input'
                      type='checkbox'
                      role='switch'
                      checked={Boolean(store.isShow)}
                      disabled={updating}
                      onChange={() => onToggleShow(store)}
                      aria-label={`Toggle visibility of ${store.name ?? store.id}`}
                    />
                  </div>
                </td>
                <td className='text-end'>
                  <Link to={`/admin/mall/store/${store.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(store)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
    </div>
  );
};

export default StoreList;
