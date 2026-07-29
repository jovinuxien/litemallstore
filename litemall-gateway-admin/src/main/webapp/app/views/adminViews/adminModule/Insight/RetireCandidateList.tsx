import {
  IRetireCandidate,
  RetireStatus,
  useDismissRetireCandidateMutation,
  useGetRetireCandidatesQuery,
} from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage, Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import ApproveRetireDialog from 'app/views/adminViews/adminModule/Insight/ApproveRetireDialog';
import { fmtDay, fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 14: retirement pipeline (/srv/private/admin/insight/retire-candidates).
// The inventory-flow scorer PROPOSES weak goods (higher score = retire
// sooner); admins batch-approve with an execution date (default next
// Wednesday) or dismiss per row. The nightly executor flips approved batches
// off-sale on their date — this view never executes anything itself. Approve
// and dismiss CAS from `proposed`; a lost race surfaces the backend's errno
// message and the tag refetch drops the stale row.

const TABS: { value: RetireStatus; label: string }[] = [
  { value: 'proposed', label: 'Proposed' },
  { value: 'approved', label: 'Approved' },
  { value: 'dismissed', label: 'Dismissed' },
  { value: 'executed', label: 'Executed' },
];

const RetireCandidateList: React.FC = () => {
  const [status, setStatus] = React.useState<RetireStatus>('proposed');
  const { data, isLoading, isFetching, isError, error } = useGetRetireCandidatesQuery({ status });
  const [dismiss, { isLoading: dismissing }] = useDismissRetireCandidateMutation();

  const [selected, setSelected] = React.useState<Set<number>>(new Set());
  const [approveIds, setApproveIds] = React.useState<number[] | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const selectable = status === 'proposed';
  const showExecuteOn = status === 'approved' || status === 'executed';
  // Goods, category, cost, retail, margin, stock, unavail, views, sales, score, why.
  const columns = 11 + (selectable ? 2 : 0) + (showExecuteOn ? 1 : 0);

  const switchTab = (next: RetireStatus) => {
    setStatus(next);
    setSelected(new Set());
    setActionError(null);
  };

  const toggle = (goodsId: number) =>
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(goodsId)) next.delete(goodsId);
      else next.add(goodsId);
      return next;
    });

  const allSelected = list.length > 0 && list.every(c => selected.has(c.goodsId));
  const toggleAll = () => setSelected(allSelected ? new Set<number>() : new Set(list.map(c => c.goodsId)));

  const onDismiss = async (c: IRetireCandidate) => {
    if (!window.confirm(`Dismiss the retirement proposal for "${c.name || `goods #${c.goodsId}`}"?`)) return;
    setActionError(null);
    const res = await dismiss({ goodsId: c.goodsId });
    if ('error' in res && res.error) {
      const httpStatus = (res.error as { status?: number | string })?.status;
      setActionError(`Request failed${httpStatus ? ` (${httpStatus})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setActionError(msg);
    setSelected(prev => {
      const next = new Set(prev);
      next.delete(c.goodsId);
      return next;
    });
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Retirement</h4>
        {selectable && (
          <button
            className='btn btn-outline-danger filter-item'
            disabled={selected.size === 0}
            onClick={() => setApproveIds(Array.from(selected))}
          >
            Approve for retirement ({selected.size})
          </button>
        )}
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Weak goods proposed by the nightly scorer (higher score = retire sooner). Approved batches go OFF-SALE on their execution date —
        still viewable, no longer buyable; reversible via the goods panel.
      </p>

      <ul className='nav nav-tabs mb-3'>
        {TABS.map(tab => (
          <li className='nav-item' key={tab.value}>
            <button type='button' className={`nav-link${status === tab.value ? ' active' : ''}`} onClick={() => switchTab(tab.value)}>
              {tab.label}
            </button>
          </li>
        ))}
      </ul>

      {isError && <div className='alert alert-danger'>Failed to load retirement candidates{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            {selectable && (
              <th style={{ width: 34 }}>
                <input type='checkbox' className='form-check-input' checked={allSelected} onChange={toggleAll} aria-label='Select all' />
              </th>
            )}
            <th>Goods</th>
            <th>Category</th>
            <th className='text-end'>Cost</th>
            <th className='text-end'>Retail</th>
            <th className='text-end'>Margin</th>
            <th className='text-end'>Stock</th>
            <th className='text-end'>Unavail. days</th>
            <th className='text-end'>Views</th>
            <th className='text-end'>Sales</th>
            <th className='text-end'>Score</th>
            {showExecuteOn && <th>Execute on</th>}
            <th>Why</th>
            {selectable && <th className='text-end'>Actions</th>}
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={columns} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={columns} className='text-center text-muted py-5'>
                No {status} retirement candidates.
              </td>
            </tr>
          ) : (
            list.map(c => (
              <tr key={c.goodsId}>
                {selectable && (
                  <td>
                    <input
                      type='checkbox'
                      className='form-check-input'
                      checked={selected.has(c.goodsId)}
                      onChange={() => toggle(c.goodsId)}
                      aria-label={`Select goods ${c.goodsId}`}
                    />
                  </td>
                )}
                <td>
                  {c.picUrl && <img src={c.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />}
                  <Link to={`/admin/goods/${c.goodsId}/insight`}>{c.name || `Goods #${c.goodsId}`}</Link>
                  <span className='text-muted small ms-1'>#{c.goodsId}</span>
                </td>
                <td>
                  {c.categoryId != null ? <Link to={`/admin/goods/categories/${c.categoryId}`}>#{c.categoryId}</Link> : <span className='text-muted'>—</span>}
                </td>
                <td className='text-end'>{fmtMoney(c.cost)}</td>
                <td className='text-end'>{fmtMoney(c.retailPrice)}</td>
                <td className='text-end'>{fmtPct(c.marginPct)}</td>
                <td className='text-end'>{fmtInt(c.stockTotal)}</td>
                <td className='text-end'>{c.unavailableDays ? <span className='text-danger'>{c.unavailableDays}</span> : fmtInt(c.unavailableDays)}</td>
                <td className='text-end'>{fmtInt(c.views)}</td>
                <td className='text-end'>{fmtInt(c.salesQty)}</td>
                <td className='text-end'>
                  <strong>{c.score != null ? Number(c.score).toFixed(1) : '—'}</strong>
                </td>
                {showExecuteOn && <td>{fmtDay(c.executeOn)}</td>}
                <td>
                  {(c.reasons ?? []).length > 0 ? (
                    <span className='small text-muted' title={(c.reasons ?? []).join('\n')}>
                      {(c.reasons ?? []).join('; ')}
                    </span>
                  ) : (
                    <span className='text-muted'>—</span>
                  )}
                </td>
                {selectable && (
                  <td className='text-end'>
                    <button className='btn btn-sm btn-outline-danger me-1' disabled={dismissing} onClick={() => onDismiss(c)}>
                      Dismiss
                    </button>
                    <button className='btn btn-sm btn-outline-secondary' onClick={() => setApproveIds([c.goodsId])}>
                      Approve
                    </button>
                  </td>
                )}
              </tr>
            ))
          )}
        </tbody>
      </table>

      {approveIds && (
        <ApproveRetireDialog
          goodsIds={approveIds}
          onClose={() => {
            setApproveIds(null);
            setSelected(new Set());
          }}
        />
      )}
    </div>
  );
};

export default RetireCandidateList;
