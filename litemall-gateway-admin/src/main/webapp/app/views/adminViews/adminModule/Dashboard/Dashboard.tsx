import { CategoryScale, Chart as ChartJS, ChartData, ChartOptions, Legend, LinearScale, LineElement, PointElement, Title, Tooltip } from 'chart.js';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { fetchDashboardTotals, fetchOrderStats } from 'app/shared/reducers/private/catalogMgn/adminStateSlice';
import { IChannelRow, useGetChannelStatQuery, useGetCjBalanceQuery } from 'app/shared/reducers/private/services/adminOrderCjApi';
import { money } from 'app/shared/util/money';
import * as React from 'react';
import { Line } from 'react-chartjs-2';

// Order-statistics dashboard, styled to the upstream litemall-admin look:
// .app-container of colored stat tiles + .box-card chart panels + an .el-table
// daily breakdown. Data flow unchanged — real order stats fetched from
// litemall-order through the gateway as an authenticated admin, with the same
// graceful "stats unavailable" fallback.

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

const PRIMARY = '#0e7c86';
const SUCCESS = '#67C23A';

const chartOpts: ChartOptions<'line'> = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: true } },
};

const moneyChartOpts: ChartOptions<'line'> = {
  ...chartOpts,
  scales: { y: { ticks: { callback: v => `$${v}` } } },
  plugins: {
    legend: { display: true },
    tooltip: { callbacks: { label: ctx => `${ctx.dataset.label}: ${money(ctx.parsed.y)}` } },
  },
};

const StatTile: React.FC<{ value: React.ReactNode; label: string; color: string }> = ({ value, label, color }) => (
  <div className='box-card' style={{ marginBottom: 0 }}>
    <div className='box-card-body' style={{ borderLeft: `4px solid ${color}` }}>
      <div className='h3 mb-0' style={{ color }}>
        {value}
      </div>
      <div className='text-muted small'>{label}</div>
    </div>
  </div>
);

const dateOnly = (d: Date): string => d.toISOString().slice(0, 10);

// One compact "by source" / "by tender" breakdown table. Amounts are optional
// on the contract — the column renders '—' when absent.
const ChannelTable: React.FC<{ title: string; keyHeader: string; rows: IChannelRow[] }> = ({ title, keyHeader, rows }) => (
  <div className='col-lg-6'>
    <div className='text-muted small mb-1'>{title}</div>
    <table className='el-table'>
      <thead>
        <tr>
          <th>{keyHeader}</th>
          <th className='text-end'>Orders</th>
          <th className='text-end'>Amount</th>
        </tr>
      </thead>
      <tbody>
        {rows.length === 0 ? (
          <tr>
            <td colSpan={3} className='text-center text-muted py-3'>
              No orders in this range.
            </td>
          </tr>
        ) : (
          rows.map(r => (
            <tr key={r.key}>
              <td>{r.key || 'UNPAID'}</td>
              <td className='text-end'>{r.count}</td>
              <td className='text-end'>{money(r.amount)}</td>
            </tr>
          ))
        )}
      </tbody>
    </table>
  </div>
);

// "Channels & tenders" card — by-source and by-tender order breakdown from
// order's /srv/private/admin/order/stat/channel (Wave 4; assumed contract).
// Defaults to the last 30 days; degrades to a muted note when the endpoint
// isn't live yet.
const ChannelStatsCard: React.FC = () => {
  const [start, setStart] = React.useState(() => dateOnly(new Date(Date.now() - 30 * 24 * 3600 * 1000)));
  const [end, setEnd] = React.useState(() => dateOnly(new Date()));
  const { data, isLoading, isFetching } = useGetChannelStatQuery({
    start: start ? `${start}T00:00:00` : undefined,
    end: end ? `${end}T23:59:59` : undefined,
  });

  return (
    <div className='box-card'>
      <div className='box-card-header d-flex align-items-center justify-content-between'>
        <span>Channels &amp; tenders {isFetching && <span className='spinner-border spinner-border-sm text-primary ms-2' role='status' />}</span>
        <span className='d-flex align-items-center gap-1'>
          <input
            className='form-control form-control-sm'
            style={{ width: 140 }}
            type='date'
            value={start}
            onChange={e => setStart(e.target.value)}
            aria-label='Channel stats from date'
          />
          <span className='text-muted small'>to</span>
          <input
            className='form-control form-control-sm'
            style={{ width: 140 }}
            type='date'
            value={end}
            onChange={e => setEnd(e.target.value)}
            aria-label='Channel stats to date'
          />
        </span>
      </div>
      <div className='box-card-body'>
        {isLoading ? (
          <div className='text-center py-4'>
            <span className='spinner-border text-primary' role='status' />
          </div>
        ) : !data || !data.available ? (
          <div className='text-muted small py-2'>
            Channel/tender statistics are not available yet (GET /srv/private/admin/order/stat/channel did not answer).
          </div>
        ) : (
          <div className='row g-3'>
            <ChannelTable title='By sales channel' keyHeader='Source' rows={data.bySource} />
            <ChannelTable title='By payment tender' keyHeader='Tender' rows={data.byTender} />
          </div>
        )}
      </div>
    </div>
  );
};

const Dashboard: React.FC = () => {
  const dispatch = useAppDispatch();
  const { rows, totals, dashboard, loading, unavailable, errorMessage } = useAppSelector(state => state.adminState);
  // CJ dropship account balance (order's /srv/private/admin/order/cj/balance,
  // Wave-3 dependency — renders '—' until the order side ships it).
  const { data: cjBalance, isError: cjBalanceError } = useGetCjBalanceQuery();
  const cjBalanceValue = !cjBalanceError && cjBalance?.available && cjBalance.amount != null ? money(cjBalance.amount) : '—';

  React.useEffect(() => {
    dispatch(fetchOrderStats());
    dispatch(fetchDashboardTotals());
  }, [dispatch]);

  const labels = rows.map(r => r.day);

  const ordersChart: ChartData<'line'> = {
    labels,
    datasets: [
      { label: 'Orders', backgroundColor: 'transparent', borderColor: PRIMARY, pointHoverBackgroundColor: '#fff', borderWidth: 2, data: rows.map(r => r.orders) },
      { label: 'Customers', backgroundColor: 'transparent', borderColor: SUCCESS, pointHoverBackgroundColor: '#fff', borderWidth: 2, data: rows.map(r => r.customers) },
    ],
  };

  const amountChart: ChartData<'line'> = {
    labels,
    datasets: [{ label: 'Revenue', backgroundColor: 'transparent', borderColor: SUCCESS, pointHoverBackgroundColor: '#fff', borderWidth: 2, data: rows.map(r => r.amount) }],
  };

  return (
    <div className='app-container'>
      <h4 className='mb-3'>
        Order statistics {loading && <span className='spinner-border spinner-border-sm text-primary ms-2' role='status' />}
      </h4>

      {unavailable && (
        <div className='alert alert-warning'>
          {errorMessage || 'Order statistics are not available yet.'}{' '}
          <span className='text-muted small'>(GET /srv/private/admin/stat/order did not answer — is goods-management up?)</span>
        </div>
      )}
      {!unavailable && errorMessage && <div className='alert alert-danger'>{errorMessage}</div>}

      <div className='row mb-3'>
        <div className='col-sm-3'>
          <StatTile value={dashboard?.orderTotal ?? totals.orders} label='Total orders' color={PRIMARY} />
        </div>
        <div className='col-sm-3'>
          <StatTile value={dashboard?.userTotal ?? totals.customers} label='Customers' color='#E6A23C' />
        </div>
        <div className='col-sm-3'>
          <StatTile
            value={dashboard?.goodsTotal ?? '—'}
            label={dashboard ? `Goods (${dashboard.productTotal} SKUs)` : 'Goods'}
            color='#909399'
          />
        </div>
        <div className='col-sm-3'>
          <StatTile value={money(totals.amount)} label='Revenue' color={SUCCESS} />
        </div>
        <div className='col-sm-3 mt-3'>
          <StatTile value={cjBalanceValue} label='CJ dropship balance' color='#F56C6C' />
        </div>
      </div>

      <ChannelStatsCard />

      {rows.length === 0 ? (
        !loading && !unavailable && <div className='text-muted text-center py-5'>No order statistics for this period.</div>
      ) : (
        <>
          <div className='row'>
            <div className='col-lg-6'>
              <div className='box-card'>
                <div className='box-card-header'>Orders &amp; customers over time</div>
                <div className='box-card-body'>
                  <div style={{ height: 300 }}>
                    <Line data={ordersChart} options={chartOpts} />
                  </div>
                </div>
              </div>
            </div>
            <div className='col-lg-6'>
              <div className='box-card'>
                <div className='box-card-header'>Revenue over time</div>
                <div className='box-card-body'>
                  <div style={{ height: 300 }}>
                    <Line data={amountChart} options={moneyChartOpts} />
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className='box-card'>
            <div className='box-card-header'>Daily breakdown</div>
            <div className='box-card-body'>
              <table className='el-table'>
                <thead>
                  <tr>
                    <th>Day</th>
                    <th className='text-end'>Orders</th>
                    <th className='text-end'>Customers</th>
                    <th className='text-end'>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.day}>
                      <td>{r.day}</td>
                      <td className='text-end'>{r.orders}</td>
                      <td className='text-end'>{r.customers}</td>
                      <td className='text-end'>{money(r.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
};

export default Dashboard;
