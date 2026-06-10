import { useAppDispatch, useAppSelector } from 'app/config/store';
import { ProductStatus } from 'app/shared/model/enumerations/product-status.model';
import { IGood } from 'app/shared/model/product/product.model';
import { setLimit, setPage, setSort } from 'app/shared/reducers/private/catalogMgn/adminGoodsSlice';
import { useGetAdminGoodsListQuery } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import * as React from 'react';
import { Badge, Button, Card, Container, Form, Spinner, Table } from 'react-bootstrap';
import { Link } from 'react-router-dom';

// Admin landing page — the goods catalogue as an inline LIST (table rows, not a
// card grid). Data comes from goods-management through the gateway as an
// authenticated admin via adminGoodsApi (Bearer admin JWT). Pagination + sort
// live in the `adminGoods` UI-state slice; the row markers are all derived from
// the goods data (IGood.status / salesQuantity / summed SKU stock), nothing
// hardcoded. Each row links to the per-item detail at /admin/goods/:id.

// Below this summed-SKU-stock count a row is flagged low-stock (amber). Above
// the sold count a "sold N" pill is highlighted. Tunable in one place.
const LOW_STOCK_THRESHOLD = 10;
const HIGH_SOLD_THRESHOLD = 100;

type BadgeVariant = 'success' | 'danger' | 'warning' | 'secondary';

const STATUS_META: Record<string, { label: string; variant: BadgeVariant }> = {
  [ProductStatus.ONSALE]: { label: 'On sale', variant: 'success' },
  [ProductStatus.OOUTOFSTOCK]: { label: 'Out of stock', variant: 'danger' },
  [ProductStatus.INREPLENISHMENT]: { label: 'Replenishing', variant: 'warning' },
  [ProductStatus.LOCKED]: { label: 'Locked', variant: 'secondary' },
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

const StatusBadge: React.FC<{ status?: ProductStatus | null }> = ({ status }) => {
  const meta = status ? STATUS_META[status] : undefined;
  if (!meta) {
    return (
      <Badge bg='light' text='dark'>
        Unknown
      </Badge>
    );
  }
  return (
    <Badge bg={meta.variant} text={meta.variant === 'warning' ? 'dark' : undefined}>
      {meta.label}
    </Badge>
  );
};

const Legend: React.FC = () => (
  <div className='d-flex flex-wrap align-items-center gap-3 small text-muted mb-2'>
    <span>Legend:</span>
    <span>
      <Badge bg='success'>On sale</Badge>
    </span>
    <span>
      <Badge bg='danger'>Out of stock</Badge>
    </span>
    <span>
      <Badge bg='warning' text='dark'>
        Replenishing
      </Badge>
    </span>
    <span>
      <Badge bg='secondary'>Locked</Badge>
    </span>
    <span>
      <Badge bg='warning' text='dark'>
        Low stock &lt; {LOW_STOCK_THRESHOLD}
      </Badge>
    </span>
    <span>
      <Badge bg='info'>sold N</Badge> highlighted &ge; {HIGH_SOLD_THRESHOLD}
    </span>
  </div>
);

const GoodsRow: React.FC<{ good: IGood }> = ({ good }) => {
  const stock = good.stock;
  const isLowStock = typeof stock === 'number' && stock < LOW_STOCK_THRESHOLD;
  const sold = good.salesQuantity ?? 0;
  const isHotSeller = sold >= HIGH_SOLD_THRESHOLD;

  return (
    <tr>
      <td style={{ width: 56 }}>
        {good.picUrl ? (
          <img src={good.picUrl} alt={good.name ?? ''} style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 4 }} />
        ) : (
          <div style={{ width: 44, height: 44, background: '#eee', borderRadius: 4 }} />
        )}
      </td>
      <td>
        <Link to={`/admin/goods/${good.id}`}>{good.name || `#${good.id}`}</Link>
        {good.brand && <div className='small text-muted'>{good.brand}</div>}
      </td>
      <td className='text-end'>¥{priceNum(good.retailPrice).toFixed(2)}</td>
      <td className='text-end'>
        <Badge bg={isHotSeller ? 'info' : 'light'} text={isHotSeller ? undefined : 'dark'}>
          sold {sold}
        </Badge>
      </td>
      <td className='text-end'>
        {typeof stock === 'number' ? stock : '—'}
        {isLowStock && (
          <Badge bg='warning' text='dark' className='ms-1'>
            low
          </Badge>
        )}
      </td>
      <td>
        <StatusBadge status={good.status} />
      </td>
      <td className='text-end'>
        <Button as={Link as any} to={`/admin/goods/${good.id}`} size='sm' variant='outline-primary'>
          Detail
        </Button>
      </td>
    </tr>
  );
};

const AdminGoodsList: React.FC = () => {
  const dispatch = useAppDispatch();
  const { page, limit, sort, order } = useAppSelector(state => state.adminGoods);

  const { data, isLoading, isFetching, isError, error } = useGetAdminGoodsListQuery({ page, limit, sort, order });

  const list = data?.list ?? [];
  const total = data?.total ?? 0;
  const pages = data?.pages ?? 0;

  const errStatus = (error as { status?: number | string })?.status;

  return (
    <Container className='admin-goods-list my-3'>
      <div className='d-flex align-items-center justify-content-between mb-2'>
        <h4 className='mb-0'>Goods {isFetching && <Spinner animation='border' size='sm' className='ms-2' />}</h4>
        <div className='d-flex align-items-center gap-2'>
          <Form.Select
            size='sm'
            style={{ width: 'auto' }}
            value={sort}
            onChange={e => dispatch(setSort({ sort: e.target.value, order }))}
            aria-label='Sort field'
          >
            {SortOptions.map(o => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </Form.Select>
          <Form.Select
            size='sm'
            style={{ width: 'auto' }}
            value={order}
            onChange={e => dispatch(setSort({ sort, order: e.target.value as 'asc' | 'desc' }))}
            aria-label='Sort direction'
          >
            <option value='desc'>Desc</option>
            <option value='asc'>Asc</option>
          </Form.Select>
          <Form.Select
            size='sm'
            style={{ width: 'auto' }}
            value={limit}
            onChange={e => dispatch(setLimit(Number(e.target.value)))}
            aria-label='Page size'
          >
            {[10, 20, 50].map(n => (
              <option key={n} value={n}>
                {n} / page
              </option>
            ))}
          </Form.Select>
        </div>
      </div>

      <Legend />

      {isError && (
        <div className='alert alert-danger'>Failed to load goods{errStatus ? ` (${errStatus})` : ''}.</div>
      )}

      <Card>
        <Table responsive hover className='mb-0 align-middle'>
          <thead className='table-light'>
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
                  <Spinner animation='border' />
                </td>
              </tr>
            ) : list.length === 0 ? (
              <tr>
                <td colSpan={7} className='text-center text-muted py-5'>
                  No goods found.
                </td>
              </tr>
            ) : (
              list.map(good => <GoodsRow key={good.id} good={good} />)
            )}
          </tbody>
        </Table>
      </Card>

      <div className='d-flex align-items-center justify-content-between mt-2'>
        <span className='small text-muted'>
          {total} item{total === 1 ? '' : 's'}
          {pages > 0 && ` · page ${page} of ${pages}`}
        </span>
        <div className='d-flex gap-2'>
          <Button size='sm' variant='outline-secondary' disabled={page <= 1 || isFetching} onClick={() => dispatch(setPage(page - 1))}>
            ‹ Prev
          </Button>
          <Button
            size='sm'
            variant='outline-secondary'
            disabled={(pages > 0 && page >= pages) || list.length < limit || isFetching}
            onClick={() => dispatch(setPage(page + 1))}
          >
            Next ›
          </Button>
        </div>
      </div>
    </Container>
  );
};

export default AdminGoodsList;
