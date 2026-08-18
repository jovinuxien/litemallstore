import { IOrderVo, orderStatusInfo, ORDER_STATUS } from 'app/shared/model/admin/order.model';
import { useListOrdersQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import {
  IPendingCjRow,
  downloadOrderExport,
  pendingItemsSummary,
  useGetCjPlacementPendingQuery,
} from 'app/shared/reducers/private/services/adminOrderCjApi';
import { money } from 'app/shared/util/money';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link, useSearchParams } from 'react-router-dom';

// Admin order list — read-only inline table sourced from litemall-order
// (/srv/private/admin/order/list) through the gateway as an authenticated
// admin. Filter by order SN / status / delivery type, sort and page; each row
// links to the read-only order detail. Wave 4 adds a CSV export of the
// current filters (+ optional start/end range) and a deliveryType filter.

// Read a BigDecimal-ish price (number or numeric string) defensively.

const PENDING_CJ_LIMIT = 20;

const OrderList: React.FC = () => {
  // Wave 23: 'pending' = paid CJ orders held for admin placement approval.
  // Held in the URL (?tab=pending) rather than component state so the dashboard's
  // pending card can deep-link straight here; anything unrecognised (or absent)
  // falls back to 'all', so every existing link keeps working unchanged.
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: 'all' | 'pending' = searchParams.get('tab') === 'pending' ? 'pending' : 'all';
  const setTab = (next: 'all' | 'pending') => {
    const params = new URLSearchParams(searchParams);
    if (next === 'pending') {
      params.set('tab', 'pending');
    } else {
      params.delete('tab');
    }
    // replace: switching tabs is a view toggle, not a navigation step to go Back through.
    setSearchParams(params, { replace: true });
  };
  const [pendingPage, setPendingPage] = React.useState(1);
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [snInput, setSnInput] = React.useState('');
  const [orderSn, setOrderSn] = React.useState('');
  const [status, setStatus] = React.useState<string>('');
  const [deliveryType, setDeliveryType] = React.useState<string>('');
  // Optional export date range (dates; expanded to ISO date-times on export).
  const [start, setStart] = React.useState('');
  const [end, setEnd] = React.useState('');
  const [exporting, setExporting] = React.useState(false);
  const [exportError, setExportError] = React.useState<string | null>(null);

  const orderStatusArray = status ? [Number(status)] : undefined;
  const { data, isLoading, isFetching, isError, error } = useListOrdersQuery({
    page,
    limit,
    sort: 'add_time',
    order,
    orderSn,
    orderStatusArray,
    deliveryType: deliveryType || undefined,
  });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  // Pending-approval feed: a limit-1 probe keeps the badge count fresh on the
  // All tab; the real page loads when the tab is active. Refetch on mount so
  // an approval done on the detail page reflects here without stale cache.
  const pendingQ = useGetCjPlacementPendingQuery(
    tab === 'pending' ? { page: pendingPage, limit: PENDING_CJ_LIMIT } : { page: 1, limit: 1 },
    { refetchOnMountOrArgChange: true },
  );
  const pendingData = pendingQ.data;
  const pendingTotal = pendingQ.isError || pendingData?.errmsg ? 0 : pendingData?.total ?? 0;
  const pendingErrStatus = (pendingQ.error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setOrderSn(snInput.trim());
  };

  // Export the CURRENT active filters (same params as the list query) plus
  // the optional start/end range, as a CSV download.
  const onExport = async () => {
    setExportError(null);
    setExporting(true);
    const msg = await downloadOrderExport({
      orderSn: orderSn || undefined,
      orderStatusArray,
      sort: 'add_time',
      order,
      deliveryType: deliveryType || undefined,
      start: start ? `${start}T00:00:00` : undefined,
      end: end ? `${end}T23:59:59` : undefined,
    });
    setExporting(false);
    if (msg) setExportError(msg);
  };

  const pendingBadge = pendingTotal > 0 && <span className='badge text-bg-warning ms-1'>{pendingTotal}</span>;

  return (
    <div className='app-container'>
      <ul className='nav nav-tabs mb-3'>
        <li className='nav-item'>
          <button type='button' className={`nav-link${tab === 'all' ? ' active' : ''}`} onClick={() => setTab('all')}>
            All orders
          </button>
        </li>
        <li className='nav-item'>
          <button type='button' className={`nav-link${tab === 'pending' ? ' active' : ''}`} onClick={() => setTab('pending')}>
            Pending CJ approval{pendingBadge}
          </button>
        </li>
      </ul>

      {tab === 'pending' ? (
        <>
          {pendingQ.isError && (
            <div className='alert alert-danger'>
              Failed to load pending CJ approvals{pendingErrStatus ? ` (${pendingErrStatus})` : ''}.
            </div>
          )}
          {pendingData?.errmsg && <div className='alert alert-danger'>{pendingData.errmsg}</div>}
          <table className='el-table'>
            <thead>
              <tr>
                <th>Order SN</th>
                <th>Paid</th>
                <th className='text-end'>Amount</th>
                <th>Consignee</th>
                <th>Items</th>
                <th>CJ ready</th>
                <th>Hold reason</th>
                <th className='text-end'>Actions</th>
              </tr>
            </thead>
            <tbody>
              {pendingQ.isLoading ? (
                <tr>
                  <td colSpan={8} className='text-center p-5'>
                    <span className='spinner-border text-primary' role='status' />
                  </td>
                </tr>
              ) : !pendingData || pendingData.list.length === 0 ? (
                <tr>
                  <td colSpan={8} className='text-center text-muted py-5'>
                    No paid CJ orders are waiting for approval.
                  </td>
                </tr>
              ) : (
                pendingData.list.map((r: IPendingCjRow) => (
                  <tr key={r.orderId ?? r.orderSn}>
                    <td>
                      <Link to={`/admin/mall/order/${r.orderId}`}>{r.orderSn || `#${r.orderId}`}</Link>
                    </td>
                    <td className='text-muted small'>{r.payTime ? r.payTime.replace('T', ' ') : '—'}</td>
                    <td className='text-end'>{money(r.actualPrice)}</td>
                    <td>
                      {r.consignee || '—'}
                      {r.country && <div className='text-muted small'>{r.country}</div>}
                    </td>
                    <td className='small'>{pendingItemsSummary(r.items)}</td>
                    <td>{r.cjReady == null ? '—' : r.cjReady ? <Tag tag='success'>Ready</Tag> : <Tag tag='warning'>Not ready</Tag>}</td>
                    <td className='text-muted small'>{r.holdReason || '—'}</td>
                    <td className='text-end'>
                      <Link to={`/admin/mall/order/${r.orderId}`} className='btn btn-sm btn-outline-primary'>
                        Review
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
          <Pagination
            page={pendingPage}
            pages={pendingData?.pages ?? 0}
            total={pendingData?.total ?? 0}
            rowCount={pendingData?.list.length ?? 0}
            limit={PENDING_CJ_LIMIT}
            busy={pendingQ.isFetching}
            onPage={setPendingPage}
          />
        </>
      ) : (
        <>
      <form className='filter-container' onSubmit={onSearch}>
        <input
          className='form-control filter-item'
          style={{ width: 200 }}
          placeholder='Order SN'
          value={snInput}
          onChange={e => setSnInput(e.target.value)}
        />
        <select
          className='form-select filter-item'
          style={{ width: 180 }}
          value={status}
          onChange={e => {
            setPage(1);
            setStatus(e.target.value);
          }}
          aria-label='Order status'
        >
          <option value=''>All statuses</option>
          {Object.entries(ORDER_STATUS).map(([code, info]) => (
            <option key={code} value={code}>
              {info.label}
            </option>
          ))}
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 130 }}
          value={deliveryType}
          onChange={e => {
            setPage(1);
            setDeliveryType(e.target.value);
          }}
          aria-label='Delivery type'
        >
          <option value=''>All delivery</option>
          <option value='pickup'>Pickup</option>
          <option value='express'>Express</option>
        </select>
        <select className='form-select filter-item' style={{ width: 120 }} value={order} onChange={e => setOrder(e.target.value as 'asc' | 'desc')} aria-label='Sort direction'>
          <option value='desc'>Newest</option>
          <option value='asc'>Oldest</option>
        </select>
        <select
          className='form-select filter-item'
          style={{ width: 120 }}
          value={limit}
          onChange={e => {
            setPage(1);
            setLimit(Number(e.target.value));
          }}
          aria-label='Page size'
        >
          {PAGE_SIZES.map(n => (
            <option key={n} value={n}>
              {n} / page
            </option>
          ))}
        </select>
        <button className='btn btn-primary filter-item' type='submit'>
          Search
        </button>
        <input
          className='form-control filter-item'
          style={{ width: 150 }}
          type='date'
          value={start}
          onChange={e => setStart(e.target.value)}
          aria-label='Export from date'
          title='Export from (inclusive)'
        />
        <input
          className='form-control filter-item'
          style={{ width: 150 }}
          type='date'
          value={end}
          onChange={e => setEnd(e.target.value)}
          aria-label='Export to date'
          title='Export to (inclusive)'
        />
        <button className='btn btn-outline-secondary filter-item' type='button' disabled={exporting} onClick={onExport}>
          {exporting ? 'Exporting…' : 'Export CSV'}
        </button>
        {isFetching && <Spinner />}
      </form>

      {exportError && <div className='alert alert-danger'>{exportError}</div>}
      {isError && <div className='alert alert-danger'>Failed to load orders{errStatus ? ` (${errStatus})` : ''}.</div>}

      <table className='el-table'>
        <thead>
          <tr>
            <th>Order SN</th>
            <th>Customer</th>
            <th>Consignee</th>
            <th className='text-end'>Amount</th>
            <th>Status</th>
            <th>Placed</th>
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
                No orders found.
              </td>
            </tr>
          ) : (
            list.map((o: IOrderVo) => {
              const st = orderStatusInfo(o.orderStatus);
              return (
                <tr key={o.id}>
                  <td>
                    <Link to={`/admin/mall/order/${o.id}`}>{o.orderSn || `#${o.id}`}</Link>
                  </td>
                  <td>{o.userName || `#${o.userId ?? '—'}`}</td>
                  <td>
                    {o.consignee || '—'}
                    {o.mobile && <div className='text-muted small'>{o.mobile}</div>}
                  </td>
                  <td className='text-end'>{money(o.actualPrice)}</td>
                  <td>
                    <Tag tag={st.tag}>{st.label}</Tag>
                    {o.deliveryType === 'pickup' && (
                      <span className='ms-1'>
                        <Tag tag='info'>Pickup</Tag>
                      </span>
                    )}
                  </td>
                  <td className='text-muted small'>{o.addTime ?? '—'}</td>
                  <td className='text-end'>
                    <Link to={`/admin/mall/order/${o.id}`} className='btn btn-sm btn-outline-primary'>
                      View
                    </Link>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      <Pagination page={page} pages={pages} total={total} rowCount={list.length} limit={limit} busy={isFetching} onPage={setPage} />
        </>
      )}
    </div>
  );
};

export default OrderList;
