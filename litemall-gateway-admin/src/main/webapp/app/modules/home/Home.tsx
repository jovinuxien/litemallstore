import { CategoryData } from 'app/shared/model/category/category.models';
import { HomeData } from 'app/shared/model/home.models';
import IconDisplay from 'bootstrap-icons/icons/display.svg';
import IconHdd from 'bootstrap-icons/icons/hdd.svg';
import IconHeadset from 'bootstrap-icons/icons/headset.svg';
import IconLaptop from 'bootstrap-icons/icons/laptop.svg';
import IconPhone from 'bootstrap-icons/icons/phone.svg';
import IconTools from 'bootstrap-icons/icons/tools.svg';
import IconTv from 'bootstrap-icons/icons/tv.svg';
import IconUpcScan from 'bootstrap-icons/icons/upc-scan.svg';
import React, { lazy, useEffect, useMemo } from 'react';
import { Link } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { IGood } from 'app/shared/model/product/product.model';
import { Card, Carousel, Col, Container, Row } from 'react-bootstrap';
import { data } from '../../data';
import { getProductList } from '../product/productSlice';
import './home.scss';

import axios, { isCancel, AxiosError } from 'axios';
import { getHomeData } from './homeSlice';
import { CategoryList } from 'app/loadingModule';
import { getFirstCategories } from '../Category/categorySlice';
import { DatasetController } from 'chart.js/dist';

const Support = lazy(() => import('../../components/userComponents/Support'));
const Banner = lazy(() => import('../../components/userComponents/carousel/Banner'));

/* const Banner1 = lazy(() => import('../../components/carousel/Banner1'));
 */
// const Carousel = lazy(() => import('../../components/carousel/CustomCarousel'));
const CardIcon = lazy(() => import('../../components/userComponents/card/CardIcon'));
const CardLogin = lazy(() => import('../../components/userComponents/card/CardLogin'));
const CardImage = lazy(() => import('../../components/userComponents/card/CardImage'));
const CardDealsOfTheDay = lazy(() => import('../../components/userComponents/card/CardDealsOfTheDay'));
interface IconProduct {
  img: string;
  title: string;
  text: string;
  tips: string;
  cssClass: string;
  to: string;
}

interface Props {
  entities?: HomeData;
  categoriesListHome?: CategoryData[];
}

const chunkArray = <T,>(arr: T[], chunkSize: number): T[][] => {
  return Array.from({ length: Math.ceil(arr.length / chunkSize) }, (_, i) => arr.slice(i * chunkSize, i * chunkSize + chunkSize));
};

// Goods-management now returns prices as LitemallMoney ({amount: BigDecimal})
// instead of a flat number, but IGood still types them as number. Read the
// numeric value out of either shape so JSX never receives an object.
const priceNum = (v: unknown): number => {
  if (v == null) return 0;
  if (typeof v === 'number') return v;
  if (typeof v === 'object' && 'amount' in (v as Record<string, unknown>)) {
    return Number((v as { amount: unknown }).amount) || 0;
  }
  return Number(v) || 0;
};


const HomeView: React.FC<Props> = ({ categoriesListHome, entities }) => {

  // We make sure we're checking for loading states and null data
  // Component-mounted side effect
  const dispatch = useAppDispatch();

  const { list } = useAppSelector(state => state.product?.data || { list: [] });
  const { categoryIndexData } =  useAppSelector(state => state.home || {});
  //const { categoryList } = useAppSelector(state => state.category.data.l1CatList || {});
  const [activeIndex, setActiveIndex] = React.useState(0);
  const [iconProducts, setIconProducts] = React.useState<IconProduct[]>(data.iconProducts);
  const [visibleProducts, setVisibleProducts] = React.useState<number>(4);
  const [loading, setLoading] = React.useState(false);

  

  /* const allFloorGoods: IGood[] = entities?.floorGoodsList?.reduce(
    (acc, floorGood) => [...acc, ...floorGood.goodsList], []); */
    const allFloorGoods: IGood[] = entities?.floorGoodsList?.reduce(
      (acc, floorGood) => [...acc, ...(floorGood.goodsList || [])],
      []
    ) || [];
  



  const bannerChunks = chunkArray(entities?.banner || [], 1);
  const newDealChunks = chunkArray(entities?.newGoodsList || [], 4);
  const rows = Array.from({ length: Math.ceil(list.length / 4) }, (_, idx) => list.slice(idx * 4, idx * 4 + 4));

  const components = useMemo(
    () => ({
      IconLaptop,
      IconHeadset,
      IconPhone,
      IconTv,
      IconDisplay,
      IconHdd,
      IconUpcScan,
      IconTools,
    }),
    []
  );

  const loadMoreProducts = () => {
    if (loading) return;
    setLoading(true);

    setTimeout(() => {
      setVisibleProducts(prev => prev + 4);
      setLoading(false);
    }, 1000);
  };

  const handleScroll = () => {
    if (window.innerHeight + window.scrollY >= document.body.offsetHeight - 100) {
      loadMoreProducts();
    }
  };

  const handleSelect = (selectedIndex: number) => {
    setActiveIndex(selectedIndex);
  };

  
 

  useEffect(() => {
    // App1.tsx already drives getHomeData + getFirstCategories at boot. Only
    // dispatch what HomeView owns exclusively (the "Deals" product list).
    dispatch(getProductList());
    window.addEventListener('scroll', handleScroll);
    return () => {
      window.removeEventListener('scroll', handleScroll);
    };
  }, [dispatch]);

  

  const carouselContent = rows.map((row, idx) => (
    <div className={`carousel-item ${idx === 0 ? 'active' : ''}`} key={idx}>
      <div className='row g-3'>
        {row.map((product) => {
          //const ProductImage = components[product.picUrl];
          return (
            <div key={`product-${product.id}`} className='col-md-3'>
              <CardIcon title={product.brief} text={product.description} tips={product.description} to={product.picUrl}>
                <img src={product.picUrl} className={''} width='80' height='80' />
              </CardIcon>
            </div>
          );
        })}
      </div>
    </div>
  ));


 


  return (
    <div className='groupon-style-home'>
      {/* Hero Banner */}
      <Container>
        <Row>
          <Col md={12}>
            <header className='hero-banner'>
              <Carousel>
                {bannerChunks?.map((chunk, idx) => (
                  // <Carousel.Item key={idx}>
                  <Carousel.Item key={`banner-${idx}`}>
                    <img className='d-block w-100 h-75' src={chunk[0]?.url} alt={chunk[0]?.name} />
                    <Carousel.Caption>
                      <h3>{chunk[0]?.name}</h3>
                      <p>{chunk[0]?.name}</p>
                    </Carousel.Caption>
                  </Carousel.Item>
                ))}
              </Carousel>
            </header>
          </Col>
        </Row>

        <section className='deals-of-the-day my-5'>
          <h2 className='section-title'>Hot and New goods</h2>

          <Row id='hot-and-new'>
            {allFloorGoods.slice(0, 8).map((product, idx) => (
              <Col key={idx} xs={12} md={3} className='mb-4'>
                {/* <Link to={`/product/${product.id}`} className='btn btn-primary btn-sm view-deal-button'> */}
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
                      <Card.Title
                        className='product-title h6 mb-2'
                        style={{ fontSize: '14px', fontWeight: 'bold', lineHeight: '1.2', fontFamily: 'Helvetica Neue, Arial, sans-serif' }}
                      >
                        {product.name}
                      </Card.Title>
                      <Card.Text
                        className='product-brief flex-grow-1 mb-3'
                        style={{
                          fontSize: '13px',
                          fontFamily: 'Helvetica Neue, Arial, sans-serif',
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
                            <span className='current-price'>${priceNum(product.retailPrice)}</span>
                            <span className='original-price'>${priceNum(product.counterPrice)}</span>
                          </div>
                          <div className='discount-badge'>{Math.round(((priceNum(product.counterPrice) - priceNum(product.retailPrice)) / (priceNum(product.counterPrice) || 1)) * 100)}% OFF</div>
                        </div>

                        <span className='btn btn-primary btn-sm view-deal-button'>Detail</span>
                      </div>
                    </Card.Body>
                  </Card>
                </Link>
              </Col>
            ))}
          </Row>
        </section>
        <section>
          <h2 className='text-center mb-4'>Shop by Category</h2>

          <Row>
         {/*  {categoryList.slice(0, 6).map((category) => {
            // Generate a safe key
            //const safeKey = category.categoryId ? `category-${category.categoryId}` : `category-index-${index}`;
            
            return (
              // <Col key={safeKey} xs={6} sm={4} md={3} lg={2} className='mb-4'>
              <Col key={ `category-${category.categoryId.id}`} xs={6} sm={4} md={3} lg={2} className='mb-4'>
                <Card className='text-center h-100 shadow-sm'>
                  <Card.Img 
                    variant='top' 
                    src={category.iconUrl || '/default-category-icon.png'} 
                    alt={category.categoryName} 
                    className='p-3' 
                    style={{ objectFit: 'contain', height: '120px' }} 
                  />
                  <Card.Body>
                    <Card.Title className='fs-6'>{category.categoryName || 'Unnamed Category'}</Card.Title>
                  </Card.Body>
                  <Card.Footer>
                    <Link 
                      to={category.categoryId.id ? `/category/${category.categoryId.id}` : '#'} 
                      className='btn btn-sm btn-outline-primary'
                    >
                      Shop Now
                    </Link>
                  </Card.Footer>
                </Card>
              </Col>
            );
          })} */}
        </Row>
        </section>

        {/* <section className='shop-by-category my-4'>
          <h2 className='text-center mb-4'>Shop by Category</h2>
          <Row className='mb-5'>
            {categoriesListHome.map((category, index) => (
              <Col key={category.id} xs={3} sm={2} md={2} lg={2}>
                <Link to={`/category/${category.id}`} className='text-decoration-none'>
                  <Card className='text-center h-100 border-0'>
                    <Card.Img
                      variant='top'
                      src={category.picUrl}
                      alt={category.name}
                      className='rounded-circle mx-auto d-block'
                      style={{ width: '64px', height: '64px', objectFit: 'cover' }}
                    />
                    <Card.Body className='p-2'>
                      <Card.Title className='fs-6 text-muted'>{category.name}</Card.Title>
                    </Card.Body>
                  </Card>
                </Link>
              </Col>
            ))}
          </Row>
        </section> */}
        {/* Banner section */}
        <section className='featured-section'>
          <h2 className='section-title mb-4'>Featured Categories</h2>
          {/* <Row className='mb-5'>
            {(entities?.banner || []).slice(0, 2).map((bann, idx) => (
              <Col key={idx} md={6} className='mb-3'>
                <Link to={`/category/${bann.id}`} className='banner-item'>
                  <img src={bann.url} alt={bann.name} className='img-fluid rounded' style={{ width: '100%', height: '300px', objectFit: 'cover' }} />
                  <div className='banner-overlay'>
                    <h3 className='banner-title'>{bann.name}</h3>
                  </div>
                </Link>
              </Col>
            ))}
          </Row> */}
          {/*  <Row className='mb-5'>
            {(entities?.banner || []).slice(0, 2).map((bann, idx) => (
              <Col key={`featured-banner-${bann.id || idx}`} md={6} className='mb-3'>
                <Link to={`/category/${bann.id}`} className='banner-item'>
                  <img src={bann.url} alt={bann.name} className='img-fluid rounded' style={{ width: '100%', height: '300px', objectFit: 'cover' }} />
                  <div className='banner-overlay'>
                    <h3 className='banner-title'>{bann.name}</h3>
                  </div>
                </Link>
              </Col>
            ))}
          </Row> */}

        </section>

        {/*  <section className='featured-section'>
          <h2 className='section-title mb-4'>Coupons</h2>
          <Row className='mb-5'>
            {entities?.couponList.slice(0, 2).map((coupon, idx) => (
              <Col key={idx} md={6} className='mb-3'>
                <Link to={`/category/${coupon.id}`} className='banner-item'>
                  <div className='banner-overlay'>
                    <h2 className='banner-title'>{coupon.name}</h2>
                    <h4 className='banner-title'>{coupon.discount}</h4>
                  </div>
                </Link>
              </Col>
            ))}
          </Row>
        </section> */}

        {/* Hot Deals */}
        <section className='hot-deals my-5'>
          <h2 className='section-title'>Deals</h2>
          <Row>
            {list.slice(0, visibleProducts).map((product, idx) => (
              <Col key={idx} xs={12} md={3} className='mb-4'>
                {/* <Link to={`/product/${product.id}`} className='btn btn-primary btn-sm view-deal-button'> */}
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
                      <Card.Title
                        className='product-title h6 mb-2'
                        style={{ fontSize: '14px', fontWeight: 'bold', lineHeight: '1.2', fontFamily: 'Helvetica Neue, Arial, sans-serif' }}
                      >
                        {product.name}
                      </Card.Title>
                      <Card.Text
                        className='product-brief flex-grow-1 mb-3'
                        style={{
                          fontSize: '13px',
                          fontFamily: 'Helvetica Neue, Arial, sans-serif',
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
                            <span className='current-price'>${priceNum(product.retailPrice)}</span>
                            <span className='original-price'>${priceNum(product.counterPrice)}</span>
                          </div>
                          <div className='discount-badge'>{Math.round(((priceNum(product.counterPrice) - priceNum(product.retailPrice)) / (priceNum(product.counterPrice) || 1)) * 100)}% OFF</div>
                        </div>

                        <span className='btn btn-primary btn-sm view-deal-button'>Detail</span>
                      </div>
                    </Card.Body>
                  </Card>
                </Link>
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
