import {
  IPromoCandidate,
  PromoKind,
  useDismissPromoCandidateMutation,
  useGetInsightCategoriesQuery,
  useGetPromoCandidatesQuery,
  useRunPromoCandidatesMutation,
} from 'app/shared/reducers/private/services/insightApi';
import { ElTag, errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import { couponPrefill, fmtPromoSuggestion, grouponPrefill } from 'app/views/adminViews/adminModule/Insight/promoFormat';
import * as React from 'react';
import { Link, useNavigate } from 'react-router-dom';

// Wave 19: promo-candidate suggestions (/srv/private/admin/insight/
// promo-candidates). The nightly scorer PROPOSES margin-guarded coupon and
// groupon promotions; this panel renders them per kind/day and hands a row to
// the EXISTING CouponForm / GrouponRuleForm prefilled via router state — the
// form then saves through the real promotion path (margin guard included) and
// fires `consume` fail-soft with the created id. Dismiss CAS-flips from
// `proposed` (errno 653 on a lost race surfaces inline; the tag refetch drops
// the stale row). Groupon creations stay DRAFT — the Phase-3 gating decision.

const KIND_TABS: { value: PromoKind; label: string }[] = [
  { value: 'coupon', label: 'Coupon' },
  { value: 'groupon', label: 'Groupon' },
];

const STATUSES = ['proposed', 'dismissed', 'consumed'] as const;

const tierTag = (tier?: string): ElTag => {
  const t = (tier || '').toLowerCase();
  if (t.includes('hot')) return 'danger';
  if (t.includes('featured')) return 'warning';
  return 'primary';
};

const statusTag = (status?: string): ElTag => {
  const s = (status || '').toLowerCase();
  if (s === 'consumed') return 'success';
  if (s === 'dismissed') return 'info';
  return 'primary';
};

const PromoCandidateList: React.FC = () => {
  const navigate = useNavigate();
  const [kind, setKind] = React.useState<PromoKind>('coupon');
  // '' = the latest scored day for the kind (server default) / all statuses.
  const [day, setDay] = React.useState<string>('');
  const [status, setStatus] = React.useState<string>('');

  const { data, isLoading, isFetching, isError, error } = useGetPromoCandidatesQuery({
    kind,
    day: day || undefined,
    status: status || undefined,
  });
  const { data: categories } = useGetInsightCategoriesQuery();
  const [dismiss, { isLoading: dismissing }] = useDismissPromoCandidateMutation();
  const [run, { isLoading: running }] = useRunPromoCandidatesMutation();

  const [actionError, setActionError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const categoryName = React.useMemo(() => {
    const byId = new Map((categories?.list ?? []).map(c => [c.categoryId, c.name]));
    return (id?: number): string | undefined => (id != null ? byId.get(id) : undefined);
  }, [categories]);

  const switchKind = (next: PromoKind) => {
    setKind(next);
    setActionError(null);
  };

  // Surface a mutation result: transport/HTTP errors and errno failures alike.
  const surface = (res: { data?: unknown; error?: unknown }) => {
    if (res.error) {
      const s = (res.error as { status?: number | string })?.status;
      setActionError(`Request failed${s ? ` (${s})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setActionError(msg);
  };

  const onDismiss = async (c: IPromoCandidate) => {
    if (!window.confirm(`Dismiss the ${kind} suggestion for "${c.name || `goods #${c.goodsId}`}"?`)) return;
    setActionError(null);
    surface(await dismiss({ goodsId: c.goodsId, kind, day: c.day }));
  };

  const onRun = async () => {
    setActionError(null);
    surface(await run({ day: day || undefined }));
  };

  // Create buttons PREFILL the existing forms via router state; the form fires
  // `consume` after a successful create (no cross-service create endpoint).
  const onCreate = (c: IPromoCandidate) => {
    if (kind === 'groupon') {
      navigate('/admin/promotion/groupon-rule/create', { state: { promoPrefill: grouponPrefill(c) } });
    } else {
      navigate('/admin/promotion/coupon/create', { state: { promoPrefill: couponPrefill(c, categoryName(c.suggestion?.categoryId)) } });
    }
  };

  return (
    <div className='app-container'>
      <ul className='nav nav-tabs mb-3'>
        {KIND_TABS.map(tab => (
          <li className='nav-item' key={tab.value}>
            <button type='button' className={`nav-link${kind === tab.value ? ' active' : ''}`} onClick={() => switchKind(tab.value)}>
              {tab.label}
            </button>
          </li>
        ))}
      </ul>

      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Promo suggestions</h4>
        <input
          type='date'
          className='form-control filter-item'
          style={{ width: 170 }}
          value={day}
          onChange={e => setDay(e.target.value)}
          aria-label='Suggestion day (empty = latest)'
        />
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={status}
          onChange={e => setStatus(e.target.value)}
          aria-label='Status filter'
        >
          <option value=''>All statuses</option>
          {STATUSES.map(s => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <button className='btn btn-outline-secondary filter-item' type='button' disabled={running} onClick={onRun}>
          {running ? 'Scoring…' : 'Re-run scoring'}
        </button>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Nightly scoring proposes margin-guarded {kind} promotions{data?.day ? ` — showing ${data.day}` : ''}. Create hands the suggestion to the
        existing form (saves through the real guard); groupon campaigns start in DRAFT and are activated from the Groupon rules panel.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load promo suggestions{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Goods</th>
            <th>Category</th>
            <th>Tier</th>
            <th className='text-end'>Score</th>
            <th className='text-end'>Cost</th>
            <th className='text-end'>Retail</th>
            <th className='text-end'>Margin</th>
            <th className='text-end'>Stock</th>
            <th className='text-end'>Rating</th>
            <th>Suggestion</th>
            <th>Status</th>
            <th>Why</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={13} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={13} className='text-center text-muted py-5'>
                No {kind} suggestions{day ? ` for ${day}` : ''}.
              </td>
            </tr>
          ) : (
            list.map(c => {
              const proposed = (c.status || 'proposed').toLowerCase() === 'proposed';
              return (
                <tr key={`${c.goodsId}-${c.day ?? ''}`}>
                  <td>
                    {c.picUrl && (
                      <img src={c.picUrl} alt='' style={{ width: 40, height: 40, objectFit: 'cover', borderRadius: 4 }} className='me-2' />
                    )}
                    <Link to={`/admin/goods/${c.goodsId}/insight`}>{c.name || `Goods #${c.goodsId}`}</Link>
                    <span className='text-muted small ms-1'>#{c.goodsId}</span>
                  </td>
                  <td>{categoryName(c.categoryId) || (c.categoryId != null ? `#${c.categoryId}` : '—')}</td>
                  <td>{c.tier ? <Tag tag={tierTag(c.tier)}>{c.tier}</Tag> : <span className='text-muted'>—</span>}</td>
                  <td className='text-end'>{c.score != null ? Number(c.score).toFixed(1) : '—'}</td>
                  <td className='text-end'>{fmtMoney(c.cost)}</td>
                  <td className='text-end'>{fmtMoney(c.retailPrice)}</td>
                  <td className='text-end'>{fmtPct(c.marginPct)}</td>
                  <td className='text-end'>{fmtInt(c.stockTotal)}</td>
                  <td className='text-end'>
                    {c.rating != null ? Number(c.rating).toFixed(1) : '—'}
                    {c.reviewCount != null && <span className='text-muted small'> ({c.reviewCount})</span>}
                  </td>
                  <td>
                    <strong>{fmtPromoSuggestion(c, categoryName(c.suggestion?.categoryId))}</strong>
                  </td>
                  <td>
                    <Tag tag={statusTag(c.status)}>{c.status || 'proposed'}</Tag>
                    {c.refId != null && (
                      <span className='text-muted small ms-1' title={`Created ${kind} id`}>
                        →#{c.refId}
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
                    <button className='btn btn-sm btn-outline-success me-1' disabled={!proposed} onClick={() => onCreate(c)}>
                      {kind === 'groupon' ? 'Create groupon' : 'Create coupon'}
                    </button>
                    <button className='btn btn-sm btn-outline-danger' disabled={dismissing || !proposed} onClick={() => onDismiss(c)}>
                      Dismiss
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
};

export default PromoCandidateList;
