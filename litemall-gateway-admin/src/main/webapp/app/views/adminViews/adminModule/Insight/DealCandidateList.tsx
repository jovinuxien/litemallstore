import {
  IDealCandidate,
  isAutoApprovedCandidate,
  useDismissDealCandidateMutation,
  useGetDealCandidatesQuery,
} from 'app/shared/reducers/private/services/insightApi';
import { ElTag, errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import ApproveDealDialog from 'app/views/adminViews/adminModule/Insight/ApproveDealDialog';
import { fmtInt, fmtMoney } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 12: daily deal proposals (/srv/private/admin/insight/deal-candidates).
// Candidates are PROPOSED by the nightly classification and become flash
// deals on an admin Approve here — or, since Wave 14 (user re-approved
// 2026-07-29), via the capped daily auto-deal tick, whose approvals carry an
// "auto" badge. Dismissed rows are never auto-approved. Dismiss records the
// decision; both actions round-trip the litemall envelope and surface
// business errors inline.

const tierTag = (tier?: string): ElTag => {
  const t = (tier || '').toLowerCase();
  if (t.includes('hot') || t === 'a' || t === 'top') return 'danger';
  if (t === 'b' || t.includes('good')) return 'warning';
  return 'primary';
};

const statusTag = (status?: string): ElTag => {
  const s = (status || '').toLowerCase();
  if (s === 'approved') return 'success';
  if (s === 'dismissed') return 'info';
  return 'primary';
};

const todayIso = (): string => new Date().toISOString().slice(0, 10);

const DealCandidateList: React.FC = () => {
  const [day, setDay] = React.useState<string>(todayIso());
  const { data, isLoading, isFetching, isError, error } = useGetDealCandidatesQuery({ day });
  const [dismiss, { isLoading: dismissing }] = useDismissDealCandidateMutation();

  const [approveFor, setApproveFor] = React.useState<IDealCandidate | null>(null);
  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const onDismiss = async (c: IDealCandidate) => {
    if (!window.confirm(`Dismiss the deal proposal for "${c.name || `goods #${c.goodsId}`}"?`)) return;
    setActionError(null);
    const res = await dismiss({ goodsId: c.goodsId });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string })?.status;
      setActionError(`Request failed${status ? ` (${status})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setActionError(msg);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Deal proposals</h4>
        <input
          type='date'
          className='form-control filter-item'
          style={{ width: 170 }}
          value={day}
          onChange={e => setDay(e.target.value || todayIso())}
          aria-label='Proposal day'
        />
        <Link className='btn btn-outline-secondary filter-item' to='/admin/promotion/deal'>
          Flash Deals panel
        </Link>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Nightly classification proposes; deals go live on approval here or via the capped daily auto-deal tick (badged “auto”). Dismissed
        proposals are never auto-approved.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load deal proposals{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th>Tier</th>
            <th className='text-end'>Score</th>
            <th className='text-end'>Cost</th>
            <th className='text-end'>Retail</th>
            <th className='text-end'>Suggested deal</th>
            <th className='text-end'>Stock</th>
            <th className='text-end'>Rating</th>
            <th>Status</th>
            <th>Why</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={11} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={11} className='text-center text-muted py-5'>
                No deal proposals for {day}.
              </td>
            </tr>
          ) : (
            list.map(c => (
              <tr key={c.goodsId}>
                <td>
                  {c.picUrl && <img src={c.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />}
                  <Link to={`/admin/goods/${c.goodsId}/insight`}>{c.name || `Goods #${c.goodsId}`}</Link>
                  <span className='text-muted small ms-1'>#{c.goodsId}</span>
                </td>
                <td>{c.tier ? <Tag tag={tierTag(c.tier)}>{c.tier}</Tag> : <span className='text-muted'>—</span>}</td>
                <td className='text-end'>{c.score != null ? Number(c.score).toFixed(1) : '—'}</td>
                <td className='text-end'>{fmtMoney(c.cost)}</td>
                <td className='text-end'>{fmtMoney(c.retailPrice)}</td>
                <td className='text-end'>
                  <strong>{fmtMoney(c.suggestedDealPrice)}</strong>
                </td>
                <td className='text-end'>{fmtInt(c.stockTotal)}</td>
                <td className='text-end'>{c.rating != null ? Number(c.rating).toFixed(1) : '—'}</td>
                <td>
                  <Tag tag={statusTag(c.status)}>{c.status || 'proposed'}</Tag>
                  {/* Wave 14: the daily tick auto-approves top candidates —
                      badge them so admins can tell auto deals from their own. */}
                  {isAutoApprovedCandidate(c) && (
                    <span className='ms-1' title='Approved by the daily auto-deal tick, not by an admin'>
                      <Tag tag='info'>auto</Tag>
                    </span>
                  )}
                </td>
                <td>
                  {(c.reasons ?? []).length > 0 ? (
                    <span className='small text-muted' title={(c.reasons ?? []).join('\n')}>
                      {(c.reasons ?? []).join('; ')}
                    </span>
                  ) : (
                    <span className='text-muted'>—</span>
                  )}
                </td>
                <td className='text-end'>
                  <button
                    className='btn btn-sm btn-outline-success me-1'
                    disabled={(c.status || '').toLowerCase() === 'approved'}
                    onClick={() => setApproveFor(c)}
                  >
                    Approve
                  </button>
                  <button
                    className='btn btn-sm btn-outline-danger'
                    disabled={dismissing || (c.status || '').toLowerCase() === 'dismissed'}
                    onClick={() => onDismiss(c)}
                  >
                    Dismiss
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {approveFor && (
        <ApproveDealDialog
          goodsId={approveFor.goodsId}
          goodsName={approveFor.name}
          cost={approveFor.cost}
          suggestedDealPrice={approveFor.suggestedDealPrice}
          defaultStock={approveFor.stockTotal}
          onClose={() => setApproveFor(null)}
        />
      )}
    </div>
  );
};

export default DealCandidateList;
