import { CategoryScale, Chart as ChartJS, ChartData, ChartOptions, Legend, LinearScale, LineElement, PointElement, Title, Tooltip } from 'chart.js';
import { StatKind, useGetStatQuery } from 'app/shared/reducers/private/services/adminStatApi';
import * as React from 'react';
import { Line } from 'react-chartjs-2';

// Generic statistics page (user / order / goods), rendering the backend StatVo
// ({ columns, rows }) as a line chart over `day` plus the raw daily table —
// same visual language as the Dashboard (box-card + el-table).

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend);

const SERIES_COLORS = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399'];

const TITLES: Record<StatKind, string> = {
  user: 'User statistics',
  order: 'Order statistics',
  goods: 'Goods statistics',
};

// Money-like columns get a currency prefix in the table.
const MONEY_COLUMNS = new Set(['amount', 'pcr']);

const numeric = (value: unknown): number => {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const StatPage: React.FC<{ kind: StatKind }> = ({ kind }) => {
  const { data, isLoading, isFetching, isError, error } = useGetStatQuery(kind);
  const columns = data?.columns ?? [];
  const rows = data?.rows ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  const labels = rows.map(r => String(r.day ?? ''));
  const seriesColumns = columns.filter(c => c !== 'day');

  const chart: ChartData<'line'> = {
    labels,
    datasets: seriesColumns.map((col, i) => ({
      label: col,
      backgroundColor: 'transparent',
      borderColor: SERIES_COLORS[i % SERIES_COLORS.length],
      pointHoverBackgroundColor: '#fff',
      borderWidth: 2,
      data: rows.map(r => numeric(r[col])),
    })),
  };

  const chartOpts: ChartOptions<'line'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: true } },
  };

  return (
    <div className='app-container'>
      <h4 className='mb-3'>
        {TITLES[kind]} {isFetching && <span className='spinner-border spinner-border-sm text-primary ms-2' role='status' />}
      </h4>

      {isError && <div className='alert alert-danger'>Failed to load statistics{errStatus ? ` (${errStatus})` : ''}.</div>}

      {rows.length === 0 ? (
        !isLoading && !isError && <div className='text-muted text-center py-5'>No statistics recorded yet.</div>
      ) : (
        <>
          <div className='box-card'>
            <div className='box-card-header'>Per day</div>
            <div className='box-card-body'>
              <div style={{ height: 320 }}>
                <Line data={chart} options={chartOpts} />
              </div>
            </div>
          </div>

          <div className='box-card'>
            <div className='box-card-header'>Daily breakdown</div>
            <div className='box-card-body'>
              <table className='el-table'>
                <thead>
                  <tr>
                    {columns.map(col => (
                      <th key={col} className={col === 'day' ? undefined : 'text-end'}>
                        {col}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={String(r.day ?? i)}>
                      {columns.map(col => (
                        <td key={col} className={col === 'day' ? undefined : 'text-end'}>
                          {col === 'day' ? String(r[col] ?? '') : MONEY_COLUMNS.has(col) ? `¥${numeric(r[col]).toFixed(2)}` : numeric(r[col])}
                        </td>
                      ))}
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

export default StatPage;
