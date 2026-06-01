import React, { useEffect, useMemo, useState } from 'react';
import { Badge, Button, Card, Col, Container, Form, Pagination, Row, Spinner } from 'react-bootstrap';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IGood } from 'app/shared/model/product/product.model';
import { searchProducts } from './searchSlice';

const PAGE_SIZE = 12;

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
  // Category can arrive either as the /category/:id path param (home tiles) or
  // as a /products?category= / /search?category= query param (facet changes).
  const categoryParam = params.id ?? searchParams.get('category');
  const category = categoryParam ? Number(categoryParam) : null;

  const [selectedBrands, setSelectedBrands] = useState<number[]>([]);
  const [minPrice, setMinPrice] = useState<string>('');
  const [maxPrice, setMaxPrice] = useState<string>('');
  const [page, setPage] = useState<number>(1);

  const { data, loading, errorMessage } = useAppSelector(state => state.search);
  const { list, total, pages, facets } = data;

  // Reset paging whenever the entry context (category / query) changes.
  useEffect(() => {
    setPage(1);
  }, [category, q]);

  useEffect(() => {
    dispatch(
      searchProducts({
        q: q || undefined,
        category,
        brands: selectedBrands,
        minPrice: minPrice === '' ? null : Number(minPrice),
        maxPrice: maxPrice === '' ? null : Number(maxPrice),
        page,
        size: PAGE_SIZE,
      })
    );
  }, [dispatch, q, category, selectedBrands, minPrice, maxPrice, page]);

  const toggleBrand = (id: number) => {
    setPage(1);
    setSelectedBrands(prev => (prev.includes(id) ? prev.filter(b => b !== id) : [...prev, id]));
  };

  // Category lives in the URL; navigate to the query form so it works whether we
  // arrived via /category/:id or /products?category=.
  const setCategory = (id: number | null) => {
    setPage(1);
    const next = new URLSearchParams();
    if (q) next.set('q', q);
    if (id != null) next.set('category', String(id));
    navigate(`/products?${next.toString()}`);
  };

  const clearAll = () => {
    setSelectedBrands([]);
    setMinPrice('');
    setMaxPrice('');
    setPage(1);
    const next = new URLSearchParams();
    if (q) next.set('q', q);
    navigate(`/products?${next.toString()}`);
  };

  const activeChips = useMemo(() => {
    const chips: { label: string; onRemove: () => void }[] = [];
    if (category != null) {
      const name = facets.categories.find(c => c.id === category)?.name ?? `Category ${category}`;
      chips.push({ label: `Category: ${name}`, onRemove: () => setCategory(null) });
    }
    selectedBrands.forEach(b => {
      const name = facets.brands.find(br => br.id === b)?.name ?? `Brand ${b}`;
      chips.push({ label: `Brand: ${name}`, onRemove: () => toggleBrand(b) });
    });
    if (minPrice !== '' || maxPrice !== '') {
      chips.push({
        label: `Price: ${minPrice || '0'} – ${maxPrice || '∞'}`,
        onRemove: () => {
          setMinPrice('');
          setMaxPrice('');
          setPage(1);
        },
      });
    }
    return chips;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [category, selectedBrands, minPrice, maxPrice, facets]);

  const hasFilters = activeChips.length > 0;

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
              {/* Category facet (single-select) */}
              <h6 className='text-uppercase text-muted small'>Category</h6>
              {facets.categories.length === 0 && <p className='text-muted small'>No categories</p>}
              <ul className='list-unstyled mb-3'>
                {facets.categories.map(c => (
                  <li key={c.id}>
                    <Form.Check
                      type='radio'
                      id={`cat-${c.id}`}
                      name='category-facet'
                      label={`${c.name} (${c.count})`}
                      checked={category === c.id}
                      onChange={() => setCategory(c.id)}
                    />
                  </li>
                ))}
              </ul>

              {/* Brand facet (multi-select) */}
              <h6 className='text-uppercase text-muted small'>Brand</h6>
              {facets.brands.length === 0 && <p className='text-muted small'>No brands</p>}
              <ul className='list-unstyled mb-3'>
                {facets.brands.map(b => (
                  <li key={b.id}>
                    <Form.Check
                      type='checkbox'
                      id={`brand-${b.id}`}
                      label={`${b.name} (${b.count})`}
                      checked={selectedBrands.includes(b.id)}
                      onChange={() => toggleBrand(b.id)}
                    />
                  </li>
                ))}
              </ul>

              {/* Price range */}
              <h6 className='text-uppercase text-muted small'>Price</h6>
              {facets.price && (
                <p className='text-muted small mb-1'>
                  Range: ${facets.price.min} – ${facets.price.max}
                </p>
              )}
              <div className='d-flex gap-2'>
                <Form.Control
                  type='number'
                  size='sm'
                  placeholder='Min'
                  value={minPrice}
                  onChange={e => {
                    setMinPrice(e.target.value);
                    setPage(1);
                  }}
                />
                <Form.Control
                  type='number'
                  size='sm'
                  placeholder='Max'
                  value={maxPrice}
                  onChange={e => {
                    setMaxPrice(e.target.value);
                    setPage(1);
                  }}
                />
              </div>
            </div>
          </div>
        </Col>

        {/* ---- Results ---- */}
        <Col md={9}>
          <div className='d-flex justify-content-between align-items-center mb-3'>
            <span className='fw-bold'>
              {total} results{q ? ` for "${q}"` : ''}
            </span>
            {loading === 'pending' && <Spinner animation='border' size='sm' />}
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
              <Pagination.Prev disabled={page <= 1} onClick={() => setPage(p => Math.max(1, p - 1))} />
              {Array.from({ length: pages }, (_, i) => i + 1).map(p => (
                <Pagination.Item key={p} active={p === page} onClick={() => setPage(p)}>
                  {p}
                </Pagination.Item>
              ))}
              <Pagination.Next disabled={page >= pages} onClick={() => setPage(p => Math.min(pages, p + 1))} />
            </Pagination>
          )}
        </Col>
      </Row>
    </Container>
  );
};

export default ProductListView;
