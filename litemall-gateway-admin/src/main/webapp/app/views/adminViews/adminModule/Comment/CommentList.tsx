import { IComment } from 'app/shared/model/admin/catalog.model';
import { useDeleteCommentMutation, useListCommentsQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { useReplyCommentMutation } from 'app/shared/reducers/private/services/adminParityApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';

// Admin product-comment moderation — list (filter by product / user), delete,
// and a one-shot admin reply (POST /srv/private/admin/comment/reply sets
// adminContent ONCE; the backend refuses a second reply with errno 622, whose
// errmsg is surfaced in the dialog). Authenticated admin →
// /srv/private/admin/comment.

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

  const { data, isLoading, isFetching, isError, error, refetch } = useListCommentsQuery({ page, limit, sort: 'add_time', order, valueId });
  const [deleteComment, { isLoading: deleting }] = useDeleteCommentMutation();
  const [replyComment, { isLoading: replying }] = useReplyCommentMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  // Reply dialog state.
  const [replyTarget, setReplyTarget] = React.useState<IComment | null>(null);
  const [replyText, setReplyText] = React.useState('');
  const [replyError, setReplyError] = React.useState<string | null>(null);

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

  const openReply = (comment: IComment) => {
    setReplyTarget(comment);
    setReplyText('');
    setReplyError(null);
  };

  const closeReply = () => {
    setReplyTarget(null);
    setReplyText('');
    setReplyError(null);
  };

  const onReplySubmit = async () => {
    if (!replyTarget?.id) return;
    if (!replyText.trim()) {
      setReplyError('Reply content is required.');
      return;
    }
    setReplyError(null);
    const res = await replyComment({ commentId: replyTarget.id, content: replyText.trim() });
    // errno 622 = reply already exists — surface the server's errmsg.
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) {
      setReplyError(msg);
      return;
    }
    closeReply();
    refetch();
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
                  <button className='btn btn-sm btn-outline-primary me-1' onClick={() => openReply(comment)}>
                    Reply
                  </button>
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

      <Modal show={replyTarget != null} onHide={closeReply} centered>
        <Modal.Header closeButton>
          <Modal.Title as='h6'>Reply to comment #{replyTarget?.id}</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className='mb-3'>
            <div className='text-muted small mb-1'>
              Customer #{replyTarget?.userId ?? '—'} on product {replyTarget?.valueId ?? '—'}:
            </div>
            <div className='border rounded p-2' style={{ whiteSpace: 'pre-wrap', background: '#fafafa' }}>
              {replyTarget?.content || <span className='text-muted'>(no content)</span>}
            </div>
          </div>
          {replyTarget?.adminContent ? (
            <div className='mb-2'>
              <div className='mb-1'>
                <Tag tag='info'>Already replied</Tag>
              </div>
              <div className='border rounded p-2' style={{ whiteSpace: 'pre-wrap', background: '#fafafa' }}>{replyTarget.adminContent}</div>
              <div className='form-text'>A comment can only be replied to once.</div>
            </div>
          ) : (
            <div className='mb-2'>
              <label className='form-label'>Admin reply</label>
              <textarea className='form-control' rows={3} value={replyText} onChange={e => setReplyText(e.target.value)} autoFocus />
              <div className='form-text'>A comment can only be replied to once — the reply cannot be edited afterwards.</div>
            </div>
          )}
          {replyError && <div className='alert alert-danger mb-0'>{replyError}</div>}
        </Modal.Body>
        <Modal.Footer>
          <Button variant='outline-secondary' size='sm' onClick={closeReply}>
            Close
          </Button>
          {!replyTarget?.adminContent && (
            <Button variant='primary' size='sm' disabled={replying} onClick={onReplySubmit}>
              {replying ? 'Sending…' : 'Send reply'}
            </Button>
          )}
        </Modal.Footer>
      </Modal>
    </div>
  );
};

export default CommentList;
