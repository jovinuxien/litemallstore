import { IStorage } from 'app/shared/model/admin/promotion-system.model';
import { useDeleteStorageMutation, useListStorageQuery, useUploadStorageMutation } from 'app/shared/reducers/private/services/adminSysApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Object-storage browser with upload + delete, authenticated admin →
// /srv/private/admin/storage (served by litemall-goods-management). Upload is a
// multipart POST of the 'file' part.

const humanSize = (n?: number): string => {
  if (n == null) return '—';
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
};

const StorageList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [nameInput, setNameInput] = React.useState('');
  const [name, setName] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListStorageQuery({ page, limit, sort: 'add_time', order, name });
  const [uploadStorage, { isLoading: uploading }] = useUploadStorageMutation();
  const [deleteStorage, { isLoading: deleting }] = useDeleteStorageMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  const fileRef = React.useRef<HTMLInputElement>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setName(nameInput.trim());
  };

  const onUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setActionError(null);
    const fd = new FormData();
    fd.append('file', file);
    const res = await uploadStorage(fd);
    if (fileRef.current) fileRef.current.value = '';
    const msg = 'data' in res ? errnoMessage(res.data) : 'Upload failed.';
    if (msg) setActionError(msg);
  };

  const onDelete = async (s: IStorage) => {
    if (!window.confirm(`Delete "${s.name ?? s.key}"?`)) return;
    setActionError(null);
    const res = await deleteStorage({ id: s.id, key: s.key });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 200 }} placeholder='File name' value={nameInput} onChange={e => setNameInput(e.target.value)} />
        <select className='form-select filter-item' style={{ width: 120 }} value={order} onChange={e => setOrder(e.target.value as 'asc' | 'desc')} aria-label='Sort direction'>
          <option value='desc'>Newest</option>
          <option value='asc'>Oldest</option>
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
        <label className='btn btn-success filter-item mb-0'>
          {uploading ? 'Uploading…' : '+ Upload file'}
          <input ref={fileRef} type='file' hidden onChange={onUpload} disabled={uploading} />
        </label>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load storage{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Preview</th>
            <th>Name</th>
            <th>Key</th>
            <th>Type</th>
            <th className='text-end'>Size</th>
            <th>Uploaded</th>
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
                No files stored.
              </td>
            </tr>
          ) : (
            list.map(s => (
              <tr key={s.id ?? s.key}>
                <td>
                  {s.type?.startsWith('image') && s.url ? (
                    <img src={s.url} alt={s.name} style={{ height: 36, width: 36, objectFit: 'cover' }} />
                  ) : (
                    <span className='text-muted'>file</span>
                  )}
                </td>
                <td>
                  <a href={s.url} target='_blank' rel='noreferrer'>
                    {s.name || s.key}
                  </a>
                </td>
                <td className='text-muted small'>{s.key}</td>
                <td className='text-muted small'>{s.type}</td>
                <td className='text-end'>{humanSize(s.size)}</td>
                <td className='text-muted small'>{s.addTime?.replace('T', ' ').slice(0, 16)}</td>
                <td className='text-end'>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(s)}>
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

export default StorageList;
