import { IBrand } from 'app/shared/model/admin/catalog.model';
import { useDeleteBrandMutation, useListBrandsQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { money } from 'app/shared/util/money';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin brand catalogue — inline list with name filter, sort and paging, plus
// create/edit/delete, all through the gateway as an authenticated admin
// (adminCatalogApi → /srv/private/admin/brand). Mirrors the AdminGoodsList look.

const SORTS = [
  { value: 'add_time', label: 'Date added' },
  { value: 'sort_order', label: 'Sort order' },
  { value: 'name', label: 'Name' },
];

const BrandList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [sort, setSort] = React.useState('add_time');
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [nameInput, setNameInput] = React.useState('');
  const [name, setName] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListBrandsQuery({ page, limit, sort, order, name });
  const [deleteBrand, { isLoading: deleting }] = useDeleteBrandMutation();
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

  const onDelete = async (brand: IBrand) => {
    if (!window.confirm(`Delete brand "${brand.name ?? brand.id}"?`)) return;
    setActionError(null);
    const res = await deleteBrand({ id: brand.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Brand name'
          value={nameInput}
          onChange={e => setNameInput(e.target.value)}
        />
        <select className='form-select filter-item' style={{ width: 160 }} value={sort} onChange={e => setSort(e.target.value)} aria-label='Sort field'>
          {SORTS.map(o => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={order}
          onChange={e => setOrder(e.target.value as 'asc' | 'desc')}
          aria-label='Sort direction'
        >
          <option value='desc'>Desc</option>
          <option value='asc'>Asc</option>
        </select>
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
        <Link className='btn btn-success filter-item' to='/admin/mall/brand/create'>
          + New brand
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load brands{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Image</th>
            <th>Name</th>
            <th>Description</th>
            <th className='text-end'>Floor price</th>
            <th className='text-end'>Sort</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={6} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-center text-muted py-5'>
                No brands found.
              </td>
            </tr>
          ) : (
            list.map(brand => (
              <tr key={brand.id}>
                <td style={{ width: 56 }}>
                  {brand.picUrl ? <img src={brand.picUrl} alt={brand.name ?? ''} className='cell-thumb' /> : <div className='cell-thumb' style={{ background: '#eee' }} />}
                </td>
                <td>
                  <Link to={`/admin/mall/brand/${brand.id}`}>{brand.name || `#${brand.id}`}</Link>
                </td>
                <td className='text-muted small'>{brand.desc}</td>
                <td className='text-end'>{money(brand.floorPrice ?? 0)}</td>
                <td className='text-end'>{brand.sortOrder ?? '—'}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/brand/${brand.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(brand)}>
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

export default BrandList;
