import {
  ICategoryInsight,
  useDeleteCategoryMarginMutation,
  useGetInsightCategoriesQuery,
  useGetMarginOverridesQuery,
} from 'app/shared/reducers/private/services/insightApi';
import { errnoMessage, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import CategoryCampaignDialog from 'app/views/adminViews/adminModule/Insight/CategoryCampaignDialog';
import MarginTuningDialog from 'app/views/adminViews/adminModule/Insight/MarginTuningDialog';
import { fmtInt, fmtMoney, fmtPct } from 'app/views/adminViews/adminModule/Insight/insightFormat';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Wave 12: "Goods by category" — CJ inventory insight per L1 category root,
// served by /srv/private/admin/insight/categories. The server returns only
// L1 roots with on-sale goods, already ranked by potential profit desc; the
// table renders that order as-is. Margin/profit are null (rendered "—")
// while CJ costs are not captured yet. Each row links to the category goods
// page and can launch a scheduled category campaign.
// Wave 14 adds per-category margin tuning: a simulate-then-apply dialog per
// row, an override badge on overridden categories, and an overrides panel
// with remove. Overrides take effect at the next nightly reprice.

const CategoryInsightList: React.FC = () => {
  const { data, isLoading, isFetching, isError, error } = useGetInsightCategoriesQuery();
  const { data: overrides } = useGetMarginOverridesQuery();
  const [removeOverride, { isLoading: removing }] = useDeleteCategoryMarginMutation();

  const [campaignFor, setCampaignFor] = React.useState<ICategoryInsight | null>(null);
  const [marginFor, setMarginFor] = React.useState<ICategoryInsight | null>(null);
  const [overrideError, setOverrideError] = React.useState<string | null>(null);

  const list = data?.list ?? [];
  const errStatus = (error as { status?: number | string })?.status;
  const overrideByCategory = new Map((overrides ?? []).map(o => [o.categoryId, o]));
  const categoryNameById = new Map(list.map(c => [c.categoryId, c.name]));

  const onRemoveOverride = async (categoryId: number) => {
    const name = categoryNameById.get(categoryId) || `category #${categoryId}`;
    if (!window.confirm(`Remove the margin override for ${name}? Goods return to the global default at the next nightly reprice.`)) return;
    setOverrideError(null);
    const res = await removeOverride({ categoryId });
    if ('error' in res && res.error) {
      const status = (res.error as { status?: number | string })?.status;
      setOverrideError(`Request failed${status ? ` (${status})` : ''}.`);
      return;
    }
    const msg = errnoMessage(res.data);
    if (msg) setOverrideError(msg);
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <h4 className='mb-0 filter-item'>Goods by category</h4>
        <Link className='btn btn-outline-primary filter-item' to='/admin/goods/deal-candidates'>
          Deal proposals
        </Link>
        <Link className='btn btn-outline-primary filter-item' to='/admin/goods/arrivals'>
          New arrivals
        </Link>
        <Link className='btn btn-outline-primary filter-item' to='/admin/goods/retire'>
          Retirement
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
            list.map(c => {
              const override = overrideByCategory.get(c.categoryId);
              return (
                <tr key={c.categoryId}>
                  <td>
                    <Link to={`/admin/goods/categories/${c.categoryId}`} state={{ name: c.name }}>
                      {c.name}
                    </Link>
                    <span className='text-muted small ms-1'>#{c.categoryId}</span>
                    {override && (
                      <span className='ms-1'>
                        <Tag tag='warning'>margin ×{override.margin ?? '?'}</Tag>
                      </span>
                    )}
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
                    <button className='btn btn-sm btn-outline-secondary me-1' onClick={() => setMarginFor(c)}>
                      Margin
                    </button>
                    <button className='btn btn-sm btn-outline-success' onClick={() => setCampaignFor(c)}>
                      Launch campaign
                    </button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {(overrides ?? []).length > 0 && (
        <div className='mt-4'>
          <h6>Margin overrides</h6>
          <p className='text-muted small'>Category overrides replace the global default at the nightly reprice.</p>
          {overrideError && <div className='alert alert-danger'>{overrideError}</div>}
          <table className='el-table' style={{ maxWidth: 560 }}>
            <thead>
              <tr>
                <th>Category</th>
                <th className='text-end'>Margin</th>
                <th className='text-end'>Actions</th>
              </tr>
            </thead>
            <tbody>
              {(overrides ?? []).map(o => (
                <tr key={o.categoryId}>
                  <td>
                    {categoryNameById.get(o.categoryId) || o.name || `Category #${o.categoryId}`}
                    <span className='text-muted small ms-1'>#{o.categoryId}</span>
                  </td>
                  <td className='text-end'>×{o.margin ?? '—'}</td>
                  <td className='text-end'>
                    <button className='btn btn-sm btn-outline-danger' disabled={removing} onClick={() => onRemoveOverride(o.categoryId)}>
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {campaignFor && (
        <CategoryCampaignDialog categoryId={campaignFor.categoryId} categoryName={campaignFor.name} onClose={() => setCampaignFor(null)} />
      )}
      {marginFor && (
        <MarginTuningDialog
          categoryId={marginFor.categoryId}
          categoryName={marginFor.name}
          currentOverride={overrideByCategory.get(marginFor.categoryId)?.margin}
          onClose={() => setMarginFor(null)}
        />
      )}
    </div>
  );
};

export default CategoryInsightList;
