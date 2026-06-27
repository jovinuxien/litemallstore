import { IComment } from 'app/shared/model/admin/catalog.model';
import { useDeleteCommentMutation, useListCommentsQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';

// Admin product-comment moderation — list (filter by product / user) + delete.
// The backend exposes only list + delete, so there is no create/edit here.
// Authenticated admin → /srv/private/admin/comment.

const Stars: React.FC<{ star?: number }> = ({ star }) => {
  const n = Math.max(0, Math.min(5, star ?? 0));
  return (
    <span title={`${n} / 5`} style={{ color: '#f7ba2a', letterSpacing: 1 }}>
      {'★'.repeat(n)}
      <span className='text-muted'>{'☆'.repeat(5 - n)}</span>
    </span>
  );
};

const CommentList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [productInput, setProductInput] = React.useState('');
  const [valueId, setValueId] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListCommentsQuery({ page, limit, sort: 'add_time', order, valueId });
  const [deleteComment, { isLoading: deleting }] = useDeleteCommentMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setValueId(productInput.trim());
  };

  const onDelete = async (comment: IComment) => {
    if (!window.confirm(`Delete comment #${comment.id}?`)) return;
    setActionError(null);
    const res = await deleteComment({ id: comment.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 180 }}
          placeholder='Product id'
          value={productInput}
          onChange={e => setProductInput(e.target.value)}
        />
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
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load comments{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th className='text-end'>Product</th>
            <th className='text-end'>User</th>
            <th>Rating</th>
            <th>Content</th>
            <th>Pictures</th>
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
                No comments found.
              </td>
            </tr>
          ) : (
            list.map(comment => (
              <tr key={comment.id}>
                <td className='text-end'>{comment.valueId ?? '—'}</td>
                <td className='text-end'>{comment.userId ?? '—'}</td>
                <td>
                  <Stars star={comment.star} />
                </td>
                <td>
                  <div>{comment.content}</div>
                  {comment.adminContent && (
                    <div className='small mt-1'>
                      <Tag tag='info'>admin reply</Tag> <span className='text-muted'>{comment.adminContent}</span>
                    </div>
                  )}
                </td>
                <td>
                  {comment.hasPicture && (comment.picUrls?.length ?? 0) > 0 ? (
                    <div className='d-flex gap-1 flex-wrap'>
                      {(comment.picUrls ?? []).slice(0, 4).map((url, i) => (
                        <img key={url || i} src={url} alt='' className='cell-thumb' />
                      ))}
                    </div>
                  ) : (
                    <span className='text-muted small'>—</span>
                  )}
                </td>
                <td className='text-end'>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(comment)}>
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

export default CommentList;
