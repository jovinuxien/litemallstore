import { CategoryScale, Chart as ChartJS, ChartData, ChartOptions, Legend, LinearScale, LineElement, PointElement, Title, Tooltip } from 'chart.js';
import { IInsightSeriesPoint, useDismissDealCandidateMutation, useGetInsightGoodsDetailQuery } from 'app/shared/reducers/private/services/insightApi';
import { ElTag, errnoMessage, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import ApproveDealDialog from 'app/views/adminViews/adminModule/Insight/ApproveDealDialog';
import { fmtDateTime, fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import PromoteComposerDialog from 'app/views/adminViews/adminModule/Social/PromoteComposerDialog';
import * as React from 'react';
import { Line } from 'react-chartjs-2';
import { Link, useParams } from 'react-router-dom';

// Wave 12: per-goods decision page (/srv/private/admin/insight/goods/{id}) —
// daily series as chart.js lines (StatPage pattern), totals cards, the
// per-variant cost/stock table, and the recommendation/deal panel with
// Approve/Dismiss plus the Wave-6 social composer. Cost/margin are null
// (rendered "—") until the CJ cost is captured; the charts simply skip null
// points (spanGaps off) instead of faking zeros.

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

const COLORS = { price: '#409EFF', cost: '#E6A23C', margin: '#67C23A', stock: '#409EFF', available: '#67C23A', views: '#909399', sales: '#F56C6C' };

const numOrNull = (v: unknown): number | null => {
  if (v == null) return null;
  if (typeof v === 'boolean') return v ? 1 : 0;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

const lineOpts = (y1?: string): ChartOptions<'line'> => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: true } },
  scales: y1
    ? {
        y: { type: 'linear', position: 'left' },
        y1: { type: 'linear', position: 'right', grid: { drawOnChartArea: false }, title: { display: true, text: y1 } },
      }
    : undefined,
});

const seriesChart = (
  rows: IInsightSeriesPoint[],
  defs: { key: keyof IInsightSeriesPoint; label: string; color: string; yAxisID?: string }[]
): ChartData<'line'> => ({
  labels: rows.map(r => r.day),
  datasets: defs.map(d => ({
    label: d.label,
    backgroundColor: 'transparent',
    borderColor: d.color,
    pointHoverBackgroundColor: '#fff',
    borderWidth: 2,
    yAxisID: d.yAxisID,
    data: rows.map(r => numOrNull(r[d.key])),
  })),
});

const dealStatusTag = (status?: string): ElTag => {
  const s = (status || '').toLowerCase();
  if (s === 'live' || s === 'running') return 'danger';
  if (s === 'enabled' || s === 'approved' || s === 'scheduled') return 'success';
  if (s === 'dismissed' || s === 'ended' || s === 'disabled') return 'info';
  return 'primary';
};

const GoodsInsight: React.FC = () => {
  const { id } = useParams<'id'>();
  const { data, isLoading, isFetching, isError, error } = useGetInsightGoodsDetailQuery(id ?? '', { skip: !id });

  const [showApprove, setShowApprove] = React.useState(false);
  const [showPromote, setShowPromote] = React.useState(false);
  const [dismiss, { isLoading: dismissing }] = useDismissDealCandidateMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);

  const errStatus = (error as { status?: number | string })?.status;
  const goods = data?.goods;
  const goodsId = Number(goods?.id ?? id);
  const series = data?.series ?? [];
  const variants = data?.variants ?? [];
  const totals = data?.totals ?? {};
  const deals = data?.deals ?? [];
  const rec = data?.recommendation;

  // Approve prefill: an explicit suggested deal price from an attached
  // proposal wins; otherwise the recommendation's suggested retail.
  const suggestedDeal = deals.find(d => d.suggestedDealPrice != null)?.suggestedDealPrice ?? rec?.suggestedRetail;
  const cost = goods?.cost ?? null;

  // A dismissible proposal = a deals[] row that is not a created flash deal
  // (no id) and not already decided.
  const hasOpenProposal = deals.some(d => d.id == null && !['approved', 'dismissed'].includes((d.status || '').toLowerCase()));

  const onDismiss = async () => {
    if (!window.confirm(`Dismiss the deal proposal for "${goods?.name || `goods #${goodsId}`}"?`)) return;
    setActionError(null);
    const res = await dismiss({ goodsId });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string })?.status;
      setActionError(`Request failed${status ? ` (${status})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setActionError(msg);
  };

  const totalsCards: { label: string; value: string }[] = [
    { label: 'Views', value: fmtInt(totals.views) },
    { label: 'Sales', value: fmtInt(totals.salesQty) },
    { label: 'Revenue', value: fmtMoney(totals.revenue) },
    { label: 'Collections', value: fmtInt(totals.collects) },
    { label: 'Comments', value: fmtInt(totals.comments) },
  ];

  return (
    <div className='app-container'>
      {isError && <div className='alert alert-danger'>Failed to load goods insight{errStatus ? ` (${errStatus})` : ''}.</div>}
      {isLoading && (
        <div className='text-center p-5'>
          <span className='spinner-border text-primary' role='status' />
        </div>
      )}

      {data && (
        <>
          <div className='filter-container d-flex align-items-center'>
            {goods?.picUrl && <img src={goods.picUrl} alt='' style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 6 }} className='me-2' />}
            <div className='me-auto'>
              <h4 className='mb-0'>
                {goods?.name || `Goods #${goodsId}`} {isFetching && <span className='spinner-border spinner-border-sm text-primary ms-1' role='status' />}
              </h4>
              <span className='text-muted small'>
                #{goodsId} · retail {fmtMoney(goods?.retailPrice)} · cost {fmtMoney(cost)}
                {goods?.isOnSale === false && (
                  <span className='ms-1'>
                    <Tag tag='info'>off sale</Tag>
                  </span>
                )}
              </span>
            </div>
            <Link to={`/admin/goods/${goodsId}`} className='btn btn-outline-secondary filter-item'>
              Goods detail
            </Link>
            <button className='btn btn-outline-primary filter-item' onClick={() => setShowPromote(true)}>
              Promote on social
            </button>
            <button className='btn btn-success filter-item' onClick={() => setShowApprove(true)}>
              Approve deal…
            </button>
          </div>

          <div className='d-flex flex-wrap gap-3 mb-3'>
            {totalsCards.map(c => (
              <div key={c.label} className='box-card mb-0' style={{ minWidth: 140, flex: '1 0 auto' }}>
                <div className='box-card-body text-center'>
                  <div className='fs-4 fw-bold'>{c.value}</div>
                  <div className='text-muted small'>{c.label}</div>
                </div>
              </div>
            ))}
          </div>

          {series.length === 0 ? (
            <div className='box-card'>
              <div className='box-card-body text-muted text-center py-4'>No daily series recorded yet — charts appear after the first nightly sync.</div>
            </div>
          ) : (
            <>
              <div className='box-card'>
                <div className='box-card-header'>Price · cost · margin</div>
                <div className='box-card-body'>
                  <div style={{ height: 280 }}>
                    <Line
                      data={seriesChart(series, [
                        { key: 'retailPrice', label: 'retail price', color: COLORS.price },
                        { key: 'cost', label: 'cost', color: COLORS.cost },
                        { key: 'marginPct', label: 'margin %', color: COLORS.margin, yAxisID: 'y1' },
                      ])}
                      options={lineOpts('margin %')}
                    />
                  </div>
                </div>
              </div>
              <div className='box-card'>
                <div className='box-card-header'>Stock · CJ availability</div>
                <div className='box-card-body'>
                  <div style={{ height: 280 }}>
                    <Line
                      data={seriesChart(series, [
                        { key: 'stockTotal', label: 'stock', color: COLORS.stock },
                        { key: 'available', label: 'available (1/0)', color: COLORS.available, yAxisID: 'y1' },
                      ])}
                      options={lineOpts('available')}
                    />
                  </div>
                </div>
              </div>
              <div className='box-card'>
                <div className='box-card-header'>Views · sales</div>
                <div className='box-card-body'>
                  <div style={{ height: 280 }}>
                    <Line
                      data={seriesChart(series, [
                        { key: 'views', label: 'views', color: COLORS.views },
                        { key: 'salesQty', label: 'sales', color: COLORS.sales },
                      ])}
                      options={lineOpts()}
                    />
                  </div>
                </div>
              </div>
            </>
          )}

          <div className='box-card'>
            <div className='box-card-header'>Variants</div>
            <div className='box-card-body'>
              <table className='el-table'>
                <thead>
                  <tr>
                    <th>Variant</th>
                    <th>CJ vid</th>
                    <th className='text-end'>Price</th>
                    <th className='text-end'>Cost</th>
                    <th className='text-end'>Stock</th>
                    <th>Available</th>
                  </tr>
                </thead>
                <tbody>
                  {variants.length === 0 ? (
                    <tr>
                      <td colSpan={6} className='text-center text-muted py-4'>
                        No variant data (not enriched yet).
                      </td>
                    </tr>
                  ) : (
                    variants.map(v => (
                      <tr key={v.productId}>
                        <td>{(v.specifications ?? []).join(' / ') || `#${v.productId}`}</td>
                        <td className='small text-muted'>{v.cjVid || '—'}</td>
                        <td className='text-end'>{fmtMoney(v.price)}</td>
                        <td className='text-end'>{fmtMoney(v.cost)}</td>
                        <td className='text-end'>{fmtInt(v.stock)}</td>
                        <td>{v.available == null ? <Tag tag='info'>unknown</Tag> : v.available ? <Tag tag='success'>yes</Tag> : <Tag tag='danger'>no</Tag>}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className='box-card'>
            <div className='box-card-header'>
              Recommendation &amp; deals
              {hasOpenProposal && (
                <button className='btn btn-sm btn-outline-danger float-end' disabled={dismissing} onClick={onDismiss}>
                  Dismiss proposal
                </button>
              )}
            </div>
            <div className='box-card-body'>
              {actionError && <div className='alert alert-danger'>{actionError}</div>}
              {rec ? (
                <div className='mb-3'>
                  <div className='mb-1'>
                    {rec.advertisable == null ? null : rec.advertisable ? <Tag tag='success'>advertisable</Tag> : <Tag tag='warning'>not advertisable</Tag>}
                    <span className='ms-2'>
                      Suggested retail <strong>{fmtMoney(rec.suggestedRetail)}</strong> · margin {fmtPct(rec.marginPct)}
                    </span>
                  </div>
                  {(rec.reasons ?? []).length > 0 && (
                    <ul className='text-muted small mb-0'>
                      {(rec.reasons ?? []).map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                  )}
                </div>
              ) : (
                <div className='text-muted mb-3'>No recommendation yet — needs a captured CJ cost and a nightly evaluation.</div>
              )}

              {deals.length > 0 && (
                <table className='el-table'>
                  <thead>
                    <tr>
                      <th>Deal</th>
                      <th className='text-end'>Price</th>
                      <th>Window</th>
                      <th>Status</th>
                    </tr>
                  </thead>
                  <tbody>
                    {deals.map((d, i) => (
                      <tr key={d.id ?? i}>
                        <td>{d.id != null ? <Link to={`/admin/promotion/deal/${d.id}`}>#{d.id}</Link> : d.tier ? `proposal (${d.tier})` : 'proposal'}</td>
                        <td className='text-end'>{fmtMoney(d.dealPrice ?? d.suggestedDealPrice)}</td>
                        <td className='small'>
                          {fmtDateTime(d.startTime)} <span className='text-muted'>→</span> {fmtDateTime(d.stopTime)}
                        </td>
                        <td>{d.status ? <Tag tag={dealStatusTag(d.status)}>{d.status}</Tag> : <span className='text-muted'>—</span>}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          </div>
        </>
      )}

      {showApprove && (
        <ApproveDealDialog
          goodsId={goodsId}
          goodsName={goods?.name}
          cost={cost}
          suggestedDealPrice={suggestedDeal}
          defaultStock={0}
          onClose={() => setShowApprove(false)}
        />
      )}
      {showPromote && <PromoteComposerDialog goodsId={goodsId} goodsName={goods?.name} onClose={() => setShowPromote(false)} />}
    </div>
  );
};

export default GoodsInsight;
