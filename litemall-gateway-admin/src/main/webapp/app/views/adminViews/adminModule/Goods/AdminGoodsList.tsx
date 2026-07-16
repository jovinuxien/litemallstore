import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ProductStatus } from 'app/shared/model/enumerations/product-status.model';
import { IGood } from 'app/shared/model/product/product.model';
import { setLimit, setPage, setSort } from 'app/shared/reducers/private/catalogMgn/adminGoodsSlice';
import { useDeleteGoodsMutation, useGetAdminGoodsListQuery } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';
import GoodsImportDialog from 'app/views/adminViews/adminModule/Goods/GoodsImportDialog';
import PromoteComposerDialog from 'app/views/adminViews/adminModule/Social/PromoteComposerDialog';
import { exportGoods, ExportFormat } from 'app/views/adminViews/adminModule/Goods/goodsExport';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin goods catalogue as an inline LIST, styled to the upstream litemall-admin
// (Element) goods/list pattern: a .filter-container toolbar above a bordered
// .el-table, with .el-tag status pills and an .el-pagination footer. Data comes
// from goods-management through the gateway as an authenticated admin via
// adminGoodsApi (Bearer admin JWT); pagination/sort live in the `adminGoods`
// UI-state slice. Row markers are all derived from the goods data
// (IGood.status / salesQuantity / summed SKU stock), nothing hardcoded.

const LOW_STOCK_THRESHOLD = 10;
const HIGH_SOLD_THRESHOLD = 100;

type ElTag = 'success' | 'danger' | 'warning' | 'info' | 'primary';

const STATUS_META: Record<string, { label: string; tag: ElTag }> = {
  [ProductStatus.ONSALE]: { label: 'On sale', tag: 'success' },
  [ProductStatus.OOUTOFSTOCK]: { label: 'Out of stock', tag: 'danger' },
  [ProductStatus.INREPLENISHMENT]: { label: 'Replenishing', tag: 'warning' },
  [ProductStatus.LOCKED]: { label: 'Locked', tag: 'info' },
};

// Prices may arrive as a plain number or as a LitemallMoney { amount }; read the
// numeric value defensively so JSX never receives an object.
const priceNum = (value: unknown): number => {
  if (value == null) return 0;
  if (typeof value === 'number') return value;
  const amount = (value as { amount?: unknown }).amount;
  return typeof amount === 'number' ? amount : 0;
};

const SortOptions: { value: string; label: string }[] = [
  { value: 'add_time', label: 'Date added' },
  { value: 'retail_price', label: 'Price' },
  { value: 'name', label: 'Name' },
];

const Tag: React.FC<{ tag: ElTag; children: React.ReactNode }> = ({ tag, children }) => (
  <span className={`el-tag el-tag--${tag}`}>{children}</span>
);

const StatusTag: React.FC<{ status?: ProductStatus | null }> = ({ status }) => {
  const meta = status ? STATUS_META[status] : undefined;
  if (!meta) return <Tag tag='info'>Unknown</Tag>;
  return <Tag tag={meta.tag}>{meta.label}</Tag>;
};

const Legend: React.FC = () => (
  <div className='d-flex flex-wrap align-items-center gap-2 small text-muted mb-2'>
    <span>Legend:</span>
    <Tag tag='success'>On sale</Tag>
    <Tag tag='danger'>Out of stock</Tag>
    <Tag tag='warning'>Replenishing</Tag>
    <Tag tag='info'>Locked</Tag>
    <Tag tag='warning'>Low stock &lt; {LOW_STOCK_THRESHOLD}</Tag>
    <span>
      <Tag tag='primary'>sold N</Tag> highlighted &ge; {HIGH_SOLD_THRESHOLD}
    </span>
  </div>
);

const GoodsRow: React.FC<{ good: IGood; onDelete: (good: IGood) => void; onPromote: (good: IGood) => void; deleting: boolean }> = ({
  good,
  onDelete,
  onPromote,
  deleting,
}) => {
  const stock = good.stock;
  const isLowStock = typeof stock === 'number' && stock < LOW_STOCK_THRESHOLD;
  const sold = good.salesQuantity ?? 0;
  const isHotSeller = sold >= HIGH_SOLD_THRESHOLD;

  return (
    <tr>
      <td style={{ width: 56 }}>
        {good.picUrl ? (
          <img src={good.picUrl} alt={good.name ?? ''} className='cell-thumb' />
        ) : (
          <div className='cell-thumb' style={{ background: '#eee' }} />
        )}
      </td>
      <td>
        <Link to={`/admin/goods/${good.id}`}>{good.name || `#${good.id}`}</Link>
        {good.brand && <div className='small text-muted'>{good.brand}</div>}
      </td>
      <td className='text-end'>¥{priceNum(good.retailPrice).toFixed(2)}</td>
      <td className='text-end'>
        <Tag tag={isHotSeller ? 'primary' : 'info'}>sold {sold}</Tag>
      </td>
      <td className='text-end'>
        {typeof stock === 'number' ? stock : '—'}
        {isLowStock && (
          <span className='ms-1'>
            <Tag tag='warning'>low</Tag>
          </span>
        )}
      </td>
      <td>
        <StatusTag status={good.status} />
      </td>
      <td className='text-end' style={{ whiteSpace: 'nowrap' }}>
        <Link to={`/admin/goods/${good.id}`} className='btn btn-sm btn-outline-primary me-1'>
          Detail
        </Link>
        <Link to={`/admin/goods/${good.id}/edit`} className='btn btn-sm btn-outline-secondary me-1'>
          Edit
        </Link>
        {/* Wave 6: social-posting composer (promotion-service) */}
        <button className='btn btn-sm btn-outline-success me-1' onClick={() => onPromote(good)}>
          Promote
        </button>
        <button className='btn btn-sm btn-outline-danger' disabled={deleting} onClick={() => onDelete(good)}>
          Delete
        </button>
      </td>
    </tr>
  );
};

const AdminGoodsList: React.FC = () => {
  const dispatch = useAppDispatch();
  const { page, limit, sort, order } = useAppSelector(state => state.adminGoods);

  const { data, isLoading, isFetching, isError, error } = useGetAdminGoodsListQuery({ page, limit, sort, order });
  const [deleteGoods, { isLoading: deleting }] = useDeleteGoodsMutation();
  const [actionError, setActionError] = React.useState<string | null>(null);
  const [showImport, setShowImport] = React.useState(false);
  const [exporting, setExporting] = React.useState(false);
  const [exportMenuOpen, setExportMenuOpen] = React.useState(false);
  const [promoteGoods, setPromoteGoods] = React.useState<IGood | null>(null);

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;

  const errStatus = (error as { status?: number | string })?.status;
  const prevDisabled = page <= 1 || isFetching;
  const nextDisabled = (pages > 0 && page >= pages) || list.length < limit || isFetching;

  const onDelete = async (good: IGood) => {
    if (!window.confirm(`Delete goods "${good.name ?? good.id}"? This also removes its SKUs.`)) return;
    setActionError(null);
    const res = await deleteGoods({ id: good.id as number });
    const msg = 'data' in res ? errnoMessage(res.data) : 'Request failed.';
    if (msg) setActionError(msg);
  };

  const onExport = async (format: ExportFormat) => {
    setActionError(null);
    setExporting(true);
    try {
      await exportGoods(format);
    } catch (e) {
      setActionError(`Export failed: ${(e as Error).message ?? e}`);
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className='app-container'>
      <div className='filter-container'>
        <select
          className='form-select filter-item'
          style={{ width: 160 }}
          value={sort}
          onChange={e => dispatch(setSort({ sort: e.target.value, order }))}
          aria-label='Sort field'
        >
          {SortOptions.map(o => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={order}
          onChange={e => dispatch(setSort({ sort, order: e.target.value as 'asc' | 'desc' }))}
          aria-label='Sort direction'
        >
          <option value='desc'>Desc</option>
          <option value='asc'>Asc</option>
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => dispatch(setLimit(Number(e.target.value)))}
          aria-label='Page size'
        >
          {[10, 20, 50].map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        <Link className='btn btn-success filter-item' to='/admin/goods/create'>
          + New goods
        </Link>
        <button className='btn btn-outline-success filter-item' type='button' onClick={() => setShowImport(true)}>
          Bulk add / Import
        </button>
        <div className='btn-group filter-item' style={{ position: 'relative' }}>
          <button
            className='btn btn-outline-secondary dropdown-toggle'
            type='button'
            disabled={exporting}
            onClick={() => setExportMenuOpen(open => !open)}
          >
            {exporting ? 'Exporting…' : 'Export'}
          </button>
          {/* controlled dropdown — bootstrap's JS bundle is not loaded in this app */}
          <ul className={`dropdown-menu${exportMenuOpen ? ' show' : ''}`} style={{ top: '100%', left: 0 }}>
            {(['xlsx', 'csv', 'json'] as ExportFormat[]).map(format => (
              <li key={format}>
                <button
                  className='dropdown-item'
                  type='button'
                  onClick={() => {
                    setExportMenuOpen(false);
                    onExport(format);
                  }}
                >
                  {format.toUpperCase()}
                </button>
              </li>
            ))}
          </ul>
        </div>
        {isFetching && <span className='spinner-border spinner-border-sm text-primary filter-item' role='status' />}
      </div>

      <Legend />

      {isError && <div className='alert alert-danger'>Failed to load goods{errStatus ? ` (${errStatus})` : ''}.</div>}
      {actionError && <div className='alert alert-danger'>{actionError}</div>}
      {showImport && <GoodsImportDialog onClose={() => setShowImport(false)} />}
      {promoteGoods?.id != null && (
        <PromoteComposerDialog goodsId={promoteGoods.id} goodsName={promoteGoods.name ?? undefined} onClose={() => setPromoteGoods(null)} />
      )}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Image</th>
            <th>Name</th>
            <th className='text-end'>Price</th>
            <th className='text-end'>Sold</th>
            <th className='text-end'>Stock</th>
            <th>Status</th>
            <th className='text-end'>Actions</th>
          </tr>
        </thead>
        <tbody>
          {isLoading ? (
            <tr>
              <td colSpan={7} className='text-center p-5'>
                <span className='spinner-border text-primary' role='status' />
              </td>
            </tr>
          ) : list.length === 0 ? (
            <tr>
              <td colSpan={7} className='text-center text-muted py-5'>
                No goods found.
              </td>
            </tr>
          ) : (
            list.map(good => <GoodsRow key={good.id} good={good} onDelete={onDelete} onPromote={setPromoteGoods} deleting={deleting} />)
          )}
        </tbody>
      </table>

      <div className='el-pagination'>
        <span className='el-pagination-total'>
          {total} item{total === 1 ? '' : 's'}
          {pages > 0 && ` · page ${page} of ${pages}`}
        </span>
        <button className='el-pager-btn' disabled={prevDisabled} onClick={() => dispatch(setPage(page - 1))}>
          ‹ Prev
        </button>
        <button className='el-pager-btn' disabled={nextDisabled} onClick={() => dispatch(setPage(page + 1))}>
          Next ›
        </button>
      </div>
    </div>
  );
};

export default AdminGoodsList;
