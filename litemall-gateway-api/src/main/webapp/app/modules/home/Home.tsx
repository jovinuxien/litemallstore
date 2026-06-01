import React, { useEffect, useState } from 'react';
import { Card, Carousel, Col, Container, Row } from 'react-bootstrap';
import { Link } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IGood } from 'app/shared/model/product/product.model';
import { getCatalogIndexData } from '../Category/categorySlice';
import { getProductList } from '../product/productSlice';
import { getHomeData } from './homeSlice';
import './home.scss';

/**
 * Goods-management returns prices either as a plain number or as a
 * `LitemallMoney { amount }` value object. Read the numeric value defensively
 * so JSX never receives an object (which Reactrenders as `[object Object]` or
 * throws under strict children typing).
 */
const priceNum = (price: unknown): number => {
  if (price == null) return 0;
  if (typeof price === 'number') return price;
  if (typeof price === 'object' && 'amount' in (price as Record<string, unknown>)) {
    return Number((price as { amount: unknown }).amount) || 0;
  }
  const n = Number(price);
  return Number.isFinite(n) ? n : 0;
};

const discountPct = (counter: number, retail: number): number => {
  if (!counter || counter <= retail) return 0;
  return Math.round(((counter - retail) / counter) * 100);
};

const ProductCard: React.FC<{ product: IGood }> = ({ product }) => {
  const retail = priceNum(product.retailPrice);
  const counter = priceNum(product.counterPrice);
  return (
    <Link to={`/product/${product.id}`} className='text-decoration-none'>
      <Card className='h-100 deal-card shadow-sm'>
        <div className='image-container'>
          <Card.Img variant='top' src={product.picUrl} className='product-image' />
          <div className='badge-container'>
            {product.isNew && <div className='badge badge-new'>New</div>}
            {product.isHot && <div className='badge badge-hot'>Hot</div>}
          </div>
        </div>
        <Card.Body className='d-flex flex-column'>
          <Card.Title className='product-title h6 mb-2' style={{ fontSize: '14px', fontWeight: 'bold', lineHeight: '1.2' }}>
            {product.name}
          </Card.Title>
          <Card.Text
            className='product-brief flex-grow-1 mb-3'
            style={{
              fontSize: '13px',
              lineHeight: '1.4',
              maxHeight: '3.9em',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              display: '-webkit-box',
              WebkitLineClamp: 3,
              WebkitBoxOrient: 'vertical',
              color: '#666',
            }}
          >
            {product.brief}
          </Card.Text>
          <div className='d-flex justify-content-between align-items-center'>
            <div className='price-container'>
              <div className='price-wrapper'>
                <span className='current-price'>${retail}</span>
                {counter > retail && <span className='original-price'>${counter}</span>}
              </div>
              {discountPct(counter, retail) > 0 && <div className='discount-badge'>{discountPct(counter, retail)}% OFF</div>}
            </div>
            <span className='btn btn-primary btn-sm view-deal-button'>Detail</span>
          </div>
        </Card.Body>
      </Card>
    </Link>
  );
};

const HomeView: React.FC = () => {
  const dispatch = useAppDispatch();
  const entities = useAppSelector(state => state.home.homeData);
  const { list } = useAppSelector(state => state.product.data);
  const { dataCategoryIndex } = useAppSelector(state => state.category.data);

  const [visibleProducts, setVisibleProducts] = useState<number>(8);
  const [loading, setLoading] = useState(false);

  // Flatten floor goods into one "hot and new" strip, guarding every level.
  const allFloorGoods: IGood[] = (entities?.floorGoodsList ?? []).reduce<IGood[]>((acc, floor) => [...acc, ...(floor?.goodsList ?? [])], []);
  const banners = entities?.banner ?? [];
  const categories = dataCategoryIndex?.categoryList ?? [];
  const deals = list ?? [];

  const loadMoreProducts = () => {
    if (loading) return;
    setLoading(true);
    setVisibleProducts(prev => prev + 4);
    setLoading(false);
  };

  const handleScroll = () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
      loadMoreProducts();
    }
  };

  useEffect(() => {
    dispatch(getHomeData());
    dispatch(getProductList());
    dispatch(getCatalogIndexData());
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, [dispatch]);

  return (
    <div className='groupon-style-home'>
      <Container>
        {/* Hero Banner */}
        <Row>
          <Col md={12}>
            <header className='hero-banner'>
              <Carousel>
                {banners.map(banner => (
                  <Carousel.Item key={banner.id}>
                    <img className='d-block w-100 h-75' src={banner.url} alt={banner.name} />
                    <Carousel.Caption>
                      <h3>{banner.name}</h3>
                    </Carousel.Caption>
                  </Carousel.Item>
                ))}
              </Carousel>
            </header>
          </Col>
        </Row>

        {/* Hot and New */}
        <section className='deals-of-the-day my-5'>
          <h2 className='section-title'>Hot and New goods</h2>
          <Row id='hot-and-new'>
            {allFloorGoods.slice(0, 8).map(product => (
              <Col key={product.id} xs={12} md={3} className='mb-4'>
                <ProductCard product={product} />
              </Col>
            ))}
          </Row>
        </section>

        {/* Categories */}
        <section>
          <h2 className='text-center mb-4'>Shop by Category</h2>
          <Row>
            {categories.slice(0, 6).map(category => (
              <Col key={category.id} xs={6} sm={4} md={3} lg={2} className='mb-4'>
                <Card className='text-center h-100 shadow-sm'>
                  <Card.Img variant='top' src={category.iconUrl} alt={category.name} className='p-3' style={{ objectFit: 'contain', height: '120px' }} />
                  <Card.Body>
                    <Card.Title className='fs-6'>{category.name}</Card.Title>
                  </Card.Body>
                  <Card.Footer>
                    <Link to={`/category/${category.id}`} className='btn btn-sm btn-outline-primary'>
                      Shop Now
                    </Link>
                  </Card.Footer>
                </Card>
              </Col>
            ))}
          </Row>
        </section>

        {/* Featured banners */}
        <section className='featured-section'>
          <h2 className='section-title mb-4'>Featured Categories</h2>
          <Row className='mb-5'>
            {banners.slice(0, 2).map(banner => (
              <Col key={banner.id} md={6} className='mb-3'>
                <Link to={`/category/${banner.id}`} className='banner-item'>
                  <img src={banner.url} alt={banner.name} className='img-fluid rounded' style={{ width: '100%', height: '300px', objectFit: 'cover' }} />
                  <div className='banner-overlay'>
                    <h3 className='banner-title'>{banner.name}</h3>
                  </div>
                </Link>
              </Col>
            ))}
          </Row>
        </section>

        {/* Deals */}
        <section className='hot-deals my-5'>
          <h2 className='section-title'>Deals</h2>
          <Row>
            {deals.slice(0, visibleProducts).map(product => (
              <Col key={product.id} xs={12} md={3} className='mb-4'>
                <ProductCard product={product} />
              </Col>
            ))}
          </Row>
          {loading && <div className='text-center'>Loading more products...</div>}
        </section>
      </Container>
    </div>
  );
};

export default HomeView;
