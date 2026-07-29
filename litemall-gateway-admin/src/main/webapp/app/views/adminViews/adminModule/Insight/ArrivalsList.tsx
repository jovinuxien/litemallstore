import { useGetArrivalsQuery } from 'app/shared/reducers/private/services/insightApi';
import { Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import { fmtDateTime, fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 14: new-arrivals insight (/srv/private/admin/insight/arrivals?runs=).
// Categories ranked by the deal quality of what just arrived — dealScore =
// average deal-candidate score of the window's arrivals per L1, window = the
// last 1 or 2 complete catalog runs. The server sorts dealScore desc and
// omits categories with zero arrivals; the table renders that order as-is.

const ArrivalsList: React.FC = () => {
  const [runs, setRuns] = React.useState<1 | 2>(1);
  const { data, isLoading, isFetching, isError, error } = useGetArrivalsQuery({ runs });

  const categories = data?.categories ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>New arrivals</h4>
        <div className='btn-group filter-item' role='group' aria-label='Arrival window'>
          {([1, 2] as const).map(n => (
            <button
              key={n}
              type='button'
              className={`btn btn-sm ${runs === n ? 'btn-primary' : 'btn-outline-primary'}`}
              onClick={() => setRuns(n)}
            >
              Last {n} run{n > 1 ? 's' : ''}
            </button>
          ))}
        </div>
        {data?.since && <span className='text-muted small filter-item'>since {fmtDateTime(data.since)}</span>}
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>
        Categories ranked by the deal quality of their newest arrivals (avg proposal score over the last {runs} catalog run
        {runs > 1 ? 's' : ''}). Categories without arrivals in the window are omitted.
      </p>

      {isError && <div className='alert alert-danger'>Failed to load arrivals{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Category</th>
            <th className='text-end'>Arrivals</th>
            <th className='text-end'>Avg margin</th>
            <th className='text-end'>Avg price</th>
            <th className='text-end'>Deal score</th>
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
          ) : categories.length === 0 ? (
            <tr>
              <td colSpan={6} className='text-center text-muted py-5'>
                No arrivals in the last {runs} catalog run{runs > 1 ? 's' : ''}.
              </td>
            </tr>
          ) : (
            categories.map(c => (
              <tr key={c.categoryId}>
                <td>
                  <Link to={`/admin/goods/categories/${c.categoryId}`} state={{ name: c.name }}>
                    {c.name || `Category #${c.categoryId}`}
                  </Link>
                  <span className='text-muted small ms-1'>#{c.categoryId}</span>
                </td>
                <td className='text-end'>{fmtInt(c.arrivals)}</td>
                <td className='text-end'>{fmtPct(c.avgMarginPct)}</td>
                <td className='text-end'>{fmtMoney(c.avgRetailPrice)}</td>
                <td className='text-end'>
                  <strong>{c.dealScore != null ? Number(c.dealScore).toFixed(1) : '—'}</strong>
                </td>
                <td className='text-end'>
                  <Link to={`/admin/goods/categories/${c.categoryId}`} state={{ name: c.name }} className='btn btn-sm btn-outline-primary'>
                    Goods
                  </Link>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

export default ArrivalsList;
