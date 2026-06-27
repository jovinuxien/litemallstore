import { IOrderVo, orderStatusInfo, ORDER_STATUS } from 'app/shared/model/admin/order.model';
import { useListOrdersQuery } from 'app/shared/reducers/private/services/adminCatalogApi';
import { PAGE_SIZES, Pagination, Spinner, Tag } from 'app/views/adminViews/adminModule/_shared/crudUi';
import * as React from 'react';
import { Link } from 'react-router-dom';

// Admin order list — read-only inline table sourced from litemall-admin-api
// (/admin/order/list) through the gateway as an authenticated admin. Filter by
// order SN / customer / consignee / status, sort and page; each row links to
// the read-only order detail.

// Read a BigDecimal-ish price (number or numeric string) defensively.
const money = (v?: number | string): string => `¥${Number(v ?? 0).toFixed(2)}`;

const OrderList: React.FC = () => {
  const [page, setPage] = React.useState(1);
  const [limit, setLimit] = React.useState(20);
  const [order, setOrder] = React.useState<'asc' | 'desc'>('desc');
  const [snInput, setSnInput] = React.useState('');
  const [orderSn, setOrderSn] = React.useState('');
  const [status, setStatus] = React.useState<string>('');

  const orderStatusArray = status ? [Number(status)] : undefined;
  const { data, isLoading, isFetching, isError, error } = useListOrdersQuery({
    page,
    limit,
    sort: 'add_time',
    order,
    orderSn,
    orderStatusArray,
  });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;
  const errStatus = (error as { status?: number | string })?.status;

  const onSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    setOrderSn(snInput.trim());
  };

  return (
    <div className='app-container'>
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
        {isFetching && <Spinner />}
      </form>

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
    </div>
  );
};

export default OrderList;
