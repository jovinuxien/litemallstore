import React, { useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Col, Container, Form, Pagination, Row, Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IGood } from 'app/shared/model/product/product.model';
import { searchProducts } from './searchSlice';

const PAGE_SIZE = 12;

// Query params handled explicitly; everything else in the URL is a facet filter
// keyed by its OCS field (category_ids, brand, price, attributes…).
const RESERVED = new Set(['q', 'sort', 'page', 'size']);

const FIELD_LABELS: Record<string, string> = {
  category_ids: 'Category',
  category_names: 'Category',
  brand: 'Brand',
  price: 'Price',
};
const humanize = (field: string): string =>
  FIELD_LABELS[field] ?? field.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());

const priceNum = (price: unknown): number => {
  if (price == null) return 0;
  if (typeof price === 'number') return price;
  if (typeof price === 'object' && 'amount' in (price as Record<string, unknown>)) {
    return Number((price as { amount: unknown }).amount) || 0;
  }
  const n = Number(price);
  return Number.isFinite(n) ? n : 0;
};

const ResultCard: React.FC<{ product: IGood }> = ({ product }) => (
  <Card className='h-100 shadow-sm'>
    <Link to={`/product/${product.id}`} className='text-decoration-none'>
      <Card.Img variant='top' src={product.picUrl} style={{ height: '180px', objectFit: 'cover' }} />
    </Link>
    <Card.Body className='d-flex flex-column'>
      <Card.Title className='h6'>{product.name}</Card.Title>
      <Card.Text className='text-muted small flex-grow-1' style={{ maxHeight: '3em', overflow: 'hidden' }}>
        {product.brief}
      </Card.Text>
      <div className='d-flex justify-content-between align-items-center'>
        <span className='fw-bold text-primary'>${priceNum(product.retailPrice)}</span>
        <Link to={`/product/${product.id}`} className='btn btn-sm btn-outline-primary'>
          View
        </Link>
      </div>
    </Card.Body>
  </Card>
);

const ProductListView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const params = useParams<{ id?: string }>();

  const q = searchParams.get('q') ?? '';
  const sort = searchParams.get('sort') ?? '';
  const page = Math.max(1, Number(searchParams.get('page') ?? '1') || 1);

  // Active facet filters = every non-reserved query param. The /category/:id
  // path param maps to the OCS `category_ids` filter (unless already in the URL).
  const filters = useMemo(() => {
    const f: Record<string, string> = {};
    searchParams.forEach((value, key) => {
      if (!RESERVED.has(key) && value) f[key] = value;
    });
    if (params.id && f.category_ids == null) f.category_ids = params.id;
    return f;
  }, [searchParams, params.id]);
  const filterKey = JSON.stringify(filters);

  const { data, loading, errorMessage } = useAppSelector(state => state.search);
  const { list, total, pages, facetGroups, sortOptions } = data;

  useEffect(() => {
    dispatch(searchProducts({ q: q || undefined, page, size: PAGE_SIZE, sort: sort || null, filters }));
    // filterKey stands in for the (stable) filters object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dispatch, q, sort, page, filterKey]);

  // Navigate to the query form so behaviour is uniform whether we arrived via
  // /category/:id or /products?…. `null` removes a filter.
  const navigateWith = (mut: { q?: string; sort?: string | null; page?: number; filters?: Record<string, string | null> }) => {
    const next = { ...filters, ...(mut.filters ?? {}) };
    const sp = new URLSearchParams();
    const nq = mut.q !== undefined ? mut.q : q;
    if (nq) sp.set('q', nq);
    const ns = mut.sort !== undefined ? mut.sort : sort;
    if (ns) sp.set('sort', ns);
    Object.entries(next).forEach(([k, v]) => {
      if (v != null && String(v).trim() !== '') sp.set(k, String(v));
    });
    const np = mut.page ?? 1;
    if (np > 1) sp.set('page', String(np));
    navigate(`/products?${sp.toString()}`);
  };

  const selectedValues = (field: string): string[] =>
    (filters[field] ?? '').split(',').map(s => s.trim()).filter(Boolean);

  const toggleTerm = (field: string, value: string) => {
    const cur = selectedValues(field);
    const nextVals = cur.includes(value) ? cur.filter(v => v !== value) : [...cur, value];
    navigateWith({ filters: { [field]: nextVals.length ? nextVals.join(',') : null }, page: 1 });
  };

  // Price interval — local inputs, applied as `price=min,max`.
  const [minPrice, setMinPrice] = useState('');
  const [maxPrice, setMaxPrice] = useState('');
  useEffect(() => {
    const [lo = '', hi = ''] = (filters.price ?? '').split(',');
    setMinPrice(lo);
    setMaxPrice(hi);
  }, [filters.price]);

  const applyPrice = () => {
    const lo = minPrice.trim();
    const hi = maxPrice.trim();
    if (!lo && !hi) {
      navigateWith({ filters: { price: null }, page: 1 });
      return;
    }
    navigateWith({ filters: { price: `${lo || '0'},${hi || '999999'}` }, page: 1 });
  };

  const clearAll = () => navigateWith({ q, sort: null, filters: Object.fromEntries(Object.keys(filters).map(k => [k, null])) });

  const activeChips = useMemo(
    () =>
      Object.entries(filters).map(([field, value]) => ({
        label: `${humanize(field)}: ${value}`,
        onRemove: () => navigateWith({ filters: { [field]: null }, page: 1 }),
      })),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [filterKey]
  );
  const hasFilters = activeChips.length > 0;

  const renderFacetGroup = (group: { field: string; type: string; entries: Array<{ value: string; count: number; selected: boolean }> }) => {
    const isPrice = group.field === 'price' || group.type === 'interval';
    if (isPrice) {
      return (
        <div key={group.field} className='mb-3'>
          <h6 className='text-uppercase text-muted small'>{humanize(group.field)}</h6>
          <div className='d-flex gap-2'>
            <Form.Control type='number' size='sm' placeholder='Min' value={minPrice} onChange={e => setMinPrice(e.target.value)} />
            <Form.Control type='number' size='sm' placeholder='Max' value={maxPrice} onChange={e => setMaxPrice(e.target.value)} />
          </div>
          <Button variant='outline-secondary' size='sm' className='mt-2 w-100' onClick={applyPrice}>
            Apply
          </Button>
        </div>
      );
    }
    const selected = selectedValues(group.field);
    return (
      <div key={group.field} className='mb-3'>
        <h6 className='text-uppercase text-muted small'>{humanize(group.field)}</h6>
        {group.entries.length === 0 && <p className='text-muted small'>No options</p>}
        <ul className='list-unstyled mb-0' style={{ maxHeight: 220, overflowY: 'auto' }}>
          {group.entries.map(entry => (
            <li key={`${group.field}-${entry.value}`}>
              <Form.Check
                type='checkbox'
                id={`f-${group.field}-${entry.value}`}
                label={`${entry.value} (${entry.count})`}
                checked={selected.includes(entry.value) || entry.selected}
                onChange={() => toggleTerm(group.field, entry.value)}
              />
            </li>
          ))}
        </ul>
      </div>
    );
  };

  return (
    <Container fluid className='my-4'>
      <Row>
        {/* ---- Facet sidebar ---- */}
        <Col md={3} className='mb-3'>
          <div className='card'>
            <div className='card-header d-flex justify-content-between align-items-center'>
              <strong>Filters</strong>
              {hasFilters && (
                <Button variant='link' size='sm' className='p-0' onClick={clearAll}>
                  Clear all
                </Button>
              )}
            </div>
            <div className='card-body'>
              {facetGroups.length === 0 && <p className='text-muted small mb-0'>No filters available for these results.</p>}
              {facetGroups.map(renderFacetGroup)}
            </div>
          </div>
        </Col>

        {/* ---- Results ---- */}
        <Col md={9}>
          <div className='d-flex justify-content-between align-items-center mb-3 gap-2'>
            <span className='fw-bold'>
              {total} results{q ? ` for "${q}"` : ''}
            </span>
            <div className='d-flex align-items-center gap-2'>
              {loading === 'pending' && <Spinner animation='border' size='sm' />}
              {sortOptions.length > 0 && (
                <Form.Select size='sm' style={{ width: 'auto' }} value={sort} onChange={e => navigateWith({ sort: e.target.value || null, page: 1 })}>
                  <option value=''>Relevance</option>
                  {sortOptions.map(o => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </Form.Select>
              )}
            </div>
          </div>

          {hasFilters && (
            <div className='mb-3 d-flex flex-wrap gap-2'>
              {activeChips.map((chip, i) => (
                <Badge key={i} bg='secondary' className='d-flex align-items-center'>
                  {chip.label}
                  <span role='button' className='ms-2' onClick={chip.onRemove} aria-label='remove filter'>
                    ×
                  </span>
                </Badge>
              ))}
            </div>
          )}

          {errorMessage && <div className='alert alert-danger'>{errorMessage}</div>}

          {loading !== 'pending' && list.length === 0 && !errorMessage && <div className='alert alert-info'>No products match your filters.</div>}

          <Row className='g-3'>
            {list.map(product => (
              <Col key={product.id} xs={12} sm={6} md={4} lg={3}>
                <ResultCard product={product} />
              </Col>
            ))}
          </Row>

          {pages > 1 && (
            <Pagination className='mt-4 justify-content-center'>
              <Pagination.Prev disabled={page <= 1} onClick={() => navigateWith({ page: page - 1 })} />
              {Array.from({ length: pages }, (_, i) => i + 1).map(p => (
                <Pagination.Item key={p} active={p === page} onClick={() => navigateWith({ page: p })}>
                  {p}
                </Pagination.Item>
              ))}
              <Pagination.Next disabled={page >= pages} onClick={() => navigateWith({ page: page + 1 })} />
            </Pagination>
          )}
        </Col>
      </Row>
    </Container>
  );
};

export default ProductListView;
