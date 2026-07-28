import { ICategoryInsight, useGetInsightCategoriesQuery } from 'app/shared/reducers/private/services/insightApi';
import { Spinner } from 'app/views/adminViews/adminModule/_shared/crudUi';
import CategoryCampaignDialog from 'app/views/adminViews/adminModule/Insight/CategoryCampaignDialog';
import { fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 12: "Goods by category" — CJ inventory insight per L1 category root,
// served by /srv/private/admin/insight/categories. The server returns only
// L1 roots with on-sale goods, already ranked by potential profit desc; the
// table renders that order as-is. Margin/profit are null (rendered "—")
// while CJ costs are not captured yet. Each row links to the category goods
// page and can launch a scheduled category campaign.

const CategoryInsightList: React.FC = () => {
  const { data, isLoading, isFetching, isError, error } = useGetInsightCategoriesQuery();
  const [campaignFor, setCampaignFor] = React.useState<ICategoryInsight | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Goods by category</h4>
        <Link className='btn btn-outline-primary filter-item' to='/admin/goods/deal-candidates'>
          Deal proposals
        </Link>
        {isFetching && <Spinner />}
      </div>
      <p className='text-muted small'>Ranked by potential profit (on-sale goods × captured margin). “—” means the CJ cost is not captured yet.</p>

      {isError && <div className='alert alert-danger'>Failed to load category insight{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Category</th>
            <th className='text-end'>On sale</th>
            <th className='text-end'>New (7d)</th>
            <th className='text-end'>Stock units</th>
            <th className='text-end'>Low stock</th>
            <th className='text-end'>Unavailable</th>
            <th className='text-end'>Avg margin</th>
            <th className='text-end'>Potential profit</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={9} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={9} className='text-center text-muted py-5'>
                {isError ? 'No data.' : 'No categories with on-sale goods yet.'}
              </td>
            </tr>
          ) : (
            list.map(c => (
              <tr key={c.categoryId}>
                <td>
                  <Link to={`/admin/goods/categories/${c.categoryId}`} state={{ name: c.name }}>
                    {c.name}
                  </Link>
                  <span className='text-muted small ms-1'>#{c.categoryId}</span>
                </td>
                <td className='text-end'>{fmtInt(c.onSaleCount)}</td>
                <td className='text-end'>{fmtInt(c.newArrivals7d)}</td>
                <td className='text-end'>{fmtInt(c.stockUnits)}</td>
                <td className='text-end'>{c.lowStockCount > 0 ? <span className='text-warning'>{c.lowStockCount}</span> : fmtInt(c.lowStockCount)}</td>
                <td className='text-end'>{c.unavailableCount > 0 ? <span className='text-danger'>{c.unavailableCount}</span> : fmtInt(c.unavailableCount)}</td>
                <td className='text-end'>{fmtPct(c.avgMarginPct)}</td>
                <td className='text-end'>
                  <strong>{fmtMoney(c.potentialProfit)}</strong>
                </td>
                <td className='text-end'>
                  <Link to={`/admin/goods/categories/${c.categoryId}`} state={{ name: c.name }} className='btn btn-sm btn-outline-primary me-1'>
                    Goods
                  </Link>
                  <button className='btn btn-sm btn-outline-success' onClick={() => setCampaignFor(c)}>
                    Launch campaign
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {campaignFor && (
        <CategoryCampaignDialog categoryId={campaignFor.categoryId} categoryName={campaignFor.name} onClose={() => setCampaignFor(null)} />
      )}
    </div>
  );
};

export default CategoryInsightList;
