import { useGetAdminGoodsDetailQuery } from 'app/shared/reducers/private/services/admingoodsrv/adminGoodsApi';
import { ProductStatus } from 'app/shared/model/enumerations/product-status.model';
import React from 'react';
import { Badge, Button, Card, Col, Container, Row, Spinner, Table } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

type BadgeVariant = 'success' | 'danger' | 'warning' | 'secondary';

const STATUS_META: Record<string, { label: string; variant: BadgeVariant }> = {
  [ProductStatus.ONSALE]: { label: 'On sale', variant: 'success' },
  [ProductStatus.OOUTOFSTOCK]: { label: 'Out of stock', variant: 'danger' },
  [ProductStatus.INREPLENISHMENT]: { label: 'Replenishing', variant: 'warning' },
  [ProductStatus.LOCKED]: { label: 'Locked', variant: 'secondary' },
};

const priceNum = (value: unknown): number => {
  if (value == null) return 0;
  if (typeof value === 'number') return value;
  const amount = (value as { amount?: unknown }).amount;
  return typeof amount === 'number' ? amount : 0;
};

const GoodsDetail: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const { data, isLoading, isError, error } = useGetAdminGoodsDetailQuery(id as string, { skip: id == null });

  if (isLoading) {
    return (
      <div className='text-center p-5'>
        <Spinner animation='border' />
      </div>
    );
  }

  const errStatus = (error as { status?: number | string })?.status;

  if (isError || !data) {
    return (
      <Container className='my-3'>
        <Button variant='link' className='px-0 mb-2' onClick={() => navigate(-1)}>
          ← Back
        </Button>
        <div className='alert alert-danger'>{isError ? `Failed to load goods${errStatus ? ` (${errStatus})` : ''}.` : 'Goods not found.'}</div>
      </Container>
    );
  }

  const { goods, specificationList = [], products = [], brand, categoryNames = [] } = data;
  // IGoodsDetail has no status; fall back gracefully if the service adds one.
  const status = (goods as unknown as { status?: ProductStatus }).status;
  const meta = status ? STATUS_META[status] : undefined;
  const gallery = goods?.gallery && goods.gallery.length > 0 ? goods.gallery : goods?.picUrl ? [goods.picUrl] : [];

  return (
    <Container className='goods-detail my-3'>
      <Button variant='link' className='px-0 mb-2' onClick={() => navigate(-1)}>
        ← Back
      </Button>

      <Row>
        <Col md={4}>
          <Card>
            {goods?.picUrl ? <Card.Img variant='top' src={goods.picUrl} /> : <div style={{ height: 240, background: '#eee' }} />}
          </Card>
          {gallery.length > 1 && (
            <div className='d-flex flex-wrap gap-2 mt-2'>
              {gallery.map((g, i) => (
                <img key={`${g}-${i}`} src={g} alt={`view ${i + 1}`} style={{ width: 56, height: 56, objectFit: 'cover', borderRadius: 4 }} />
              ))}
            </div>
          )}
        </Col>

        <Col md={8}>
          <div className='d-flex align-items-center gap-2 mb-1'>
            <h3 className='mb-0'>{goods?.name}</h3>
            {meta && (
              <Badge bg={meta.variant} text={meta.variant === 'warning' ? 'dark' : undefined}>
                {meta.label}
              </Badge>
            )}
          </div>
          {goods?.brief && <p className='text-muted'>{goods.brief}</p>}
          <hr />
          <Row className='g-3'>
            <Col xs={6} sm={3}>
              <div className='small text-muted'>Retail price</div>
              <div className='h5'>¥{priceNum(goods?.retailPrice).toFixed(2)}</div>
            </Col>
            <Col xs={6} sm={3}>
              <div className='small text-muted'>Counter price</div>
              <div className='h5'>¥{priceNum(goods?.counterPrice).toFixed(2)}</div>
            </Col>
            <Col xs={6} sm={3}>
              <div className='small text-muted'>Brand</div>
              <div className='h5'>{brand || '—'}</div>
            </Col>
            <Col xs={6} sm={3}>
              <div className='small text-muted'>Unit</div>
              <div className='h5'>{goods?.unit || '—'}</div>
            </Col>
          </Row>
          {categoryNames.length > 0 && (
            <div className='mt-3'>
              <div className='small text-muted'>Categories</div>
              <div className='d-flex flex-wrap gap-2 mt-1'>
                {categoryNames.map((c, i) => (
                  <Badge key={`${c}-${i}`} bg='light' text='dark'>
                    {c}
                  </Badge>
                ))}
              </div>
            </div>
          )}
        </Col>
      </Row>

      {specificationList.length > 0 && (
        <>
          <h5 className='mt-4'>Specifications</h5>
          <ul>
            {specificationList.map((spec, i) => (
              <li key={`${spec.name}-${i}`}>
                <strong>{spec.name}:</strong> {(spec.valueList || []).map(v => v.value).join(', ')}
              </li>
            ))}
          </ul>
        </>
      )}

      <h5 className='mt-4'>SKUs</h5>
      <Table responsive bordered size='sm'>
        <thead className='table-light'>
          <tr>
            <th>Specifications</th>
            <th className='text-end'>Price</th>
            <th className='text-end'>Stock</th>
          </tr>
        </thead>
        <tbody>
          {products.length === 0 ? (
            <tr>
              <td colSpan={3} className='text-center text-muted'>
                No SKUs.
              </td>
            </tr>
          ) : (
            products.map((p, i) => (
              <tr key={p.id ?? i}>
                <td>{(p.specifications || []).map(s => (s as unknown as { typeProduct?: string }).typeProduct ?? String(s)).join(' / ') || '—'}</td>
                <td className='text-end'>¥{priceNum(p.price).toFixed(2)}</td>
                <td className='text-end'>{p.number ?? 0}</td>
              </tr>
            ))
          )}
        </tbody>
      </Table>
    </Container>
  );
};

export default GoodsDetail;
