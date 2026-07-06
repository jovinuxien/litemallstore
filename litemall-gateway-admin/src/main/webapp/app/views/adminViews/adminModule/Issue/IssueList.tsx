import { IIssue } from 'app/shared/model/admin/catalog.model';
import { useDeleteIssueMutation, useListIssuesQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { errnoMessage, PAGE_SIZES, Pagination, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin FAQ / common-issue list with create/edit/delete, authenticated admin →
// /srv/private/admin/issue.

const IssueList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [qInput, setQInput] = React.useState('');
  const [question, setQuestion] = React.useState('');

  const { data, isLoading, isFetching, isError, error } = useListIssuesQuery({ page, limit, sort: 'add_time', order, question });
  const [deleteIssue, { isLoading: deleting }] = useDeleteIssueMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setQuestion(qInput.trim());
  };

  const onDelete = async (issue: IIssue) => {
    if (!window.confirm(`Delete issue "${issue.question ?? issue.id}"?`)) return;
    setActionError(null);
    const res = await deleteIssue({ id: issue.id });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <form className='filter-container' onSubmit={onSearch}>
        <input className='form-control filter-item' style={{ width: 220 }} placeholder='Question' value={qInput} onChange={e => setQInput(e.target.value)} />
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
        <Link className='btn btn-success filter-item' to='/admin/mall/issue/create'>
          + New issue
        </Link>
        {isFetching && <Spinner />}
      </form>

      {isError && <div className='alert alert-danger'>Failed to load issues{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th style={{ width: '40%' }}>Question</th>
            <th>Answer</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={3} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={3} className='text-center text-muted py-5'>
                No issues found.
              </td>
            </tr>
          ) : (
            list.map(issue => (
              <tr key={issue.id}>
                <td>
                  <Link to={`/admin/mall/issue/${issue.id}`}>{issue.question || `#${issue.id}`}</Link>
                </td>
                <td className='text-muted small'>{issue.answer}</td>
                <td className='text-end'>
                  <Link to={`/admin/mall/issue/${issue.id}`} className='btn btn-sm btn-outline-primary me-1'>
                    Edit
                  </Link>
                  <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(issue)}>
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

export default IssueList;
