import React, { lazy, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import './Detail.scss';

import 'react-toastify/dist/ReactToastify.css';

import { faShoppingCart, faStar } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useAppDispatch, useAppSelector } from 'app/config/hooks';
import RenderFormGroupField from 'app/helpers/renderFormGroupField';
import { IItemCart } from 'app/shared/model/cart/cart.models';
import { CardFeaturedProductData } from 'app/shared/model/product/product.model';
import { addItem } from 'app/shared/reducers/cartSlice';
import { Button, Col, Container, Row, Tab, Tabs } from 'react-bootstrap';
import { ToastContainer } from 'react-toastify';
import ProductDetailDetail from './productDetailComponent/ProductDetailDetail';
import ProductDetailIssue from './productDetailComponent/ProductDetailIssue';
import ProductGallery from './productDetailComponent/ProductGallery/ProductGallery';
import ProductHighlights from './productDetailComponent/ProductHighlights';
import ProductInfo from './productDetailComponent/ProductInfo';
import ProductOptions from './productDetailComponent/ProductOptions/ProductOptions';
import ProductPricing from './productDetailComponent/ProductPricing';
import { getProductDetail } from './productDetailSlice';
import { getRelatedGoods } from './relatedSlice';
const CardFeaturedProduct = lazy(() => import('../../components/userComponents/card/CardFeaturedProduct'));
const CardServices = lazy(() => import('../../components/userComponents/card/CardServices'));
const DetailsDetail = lazy(() => import('../../components/userComponents/others/DetailsDetail'));
const RatingsReviews = lazy(() => import('../../components/userComponents/others/RatingsReviews'));
const QuestionAnswer = lazy(() => import('../../components/userComponents/others/QuestionAnswer'));
const ShippingReturns = lazy(() => import('../../components/userComponents/others/ShippingReturns'));
const SizeChart = lazy(() => import('../../components/userComponents/others/SizeChart'));

interface Tab {
  id: string;
  label: string;
  content: React.ReactNode;
}

const ProductDetailView = () => {
  const { productId } = useParams<{ productId: string }>();

  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState<string>('details');
  const [quantity, setQuantity] = useState<number>(1);
  const [error, setError] = useState<null>(null);
  const [showFloatingCart, setShowFloatingCart] = useState(false);

  const detail = useAppSelector(state => state.productDetail.data);
  const relatedGoods = useAppSelector(state => state.relatedGoods.data);
  const { isAuthenticated } = useAppSelector(state => state.auth.data);
  const { cartList, cartTotal } = useAppSelector(state => state.cart.data);

  const tabs: Tab[] = [
    { id: 'details', label: 'Details', content: <DetailsDetail /> },
    { id: 'randr', label: 'Ratings & Reviews', content: Array.from({ length: 5 }, (_, key) => <RatingsReviews key={key} />) },
    { id: 'faq', label: 'Questions & Answers', content: Array.from({ length: 5 }, (_, key) => <QuestionAnswer key={key} />) },
    { id: 'shipping', label: 'Shipping & Returns', content: <ShippingReturns /> },
    { id: 'size', label: 'Size Chart', content: <SizeChart /> },
  ];
  if (detail) {
    const cardFeaturedProductData: CardFeaturedProductData = {
      name: detail.info.name,
      link: '/product/' + productId,
      star: 3,
      price: detail.info.retailPrice,
      retailPrice: 200,
    };
  }

  const toggleFloatingCart = () => {
    setShowFloatingCart(!showFloatingCart);
  };

  const handleAddToCart = () => {
    // Implementation
    const cartItem: IItemCart = {
      id: parseInt(productId),
      goodsId: productId,
      goodsName: detail?.info?.brief,
      number: quantity,
      picUrl: detail?.info?.gallery[0],
    };

    const existingCartItem = cartList.find(item => item.goodsId === productId);
    console.log(
      'existingCartItem: ',
      existingCartItem
      //cartList.forEach(item => console.log(item))
    );
    console.log('total', cartList.length);

    if (existingCartItem) {
      const updateCartItem = {
        ...existingCartItem,
        number: existingCartItem.number + quantity,
      };
      dispatch(addItem(updateCartItem));
    } else {
      dispatch(addItem(cartItem));
    }

    setShowFloatingCart(true);
    /* setTimeout(() => {
      setShowFloatingCart(false);
    }, 5000); */
  };

  const handleBuyNow = () => {
    // Implementation
  };

  const handleSubmit = e => {
    e.preventDefault();
  };

  useEffect(() => {
    if (productId) {
      dispatch(getProductDetail(parseInt(productId)));
      dispatch(getRelatedGoods(parseInt(productId)));
    }
    console.log('The detail good', detail);

    console.log('The detail good', detail?.attribute);

    //window.scrollTo(0, 0);
  }, [productId, dispatch]);

  const scrollToGallery = () => {
    const gallery = document.getElementById('product-gallery');
    if (gallery) {
      gallery.scrollIntoView({ behavior: 'smooth' });
    }
  };

  return (
    <div className={`main-content ${showFloatingCart ? 'sidebar-open' : ''}`}>
      <Container fluid className='product-detail'>
        <Row>
          <Col lg={9}>
            <Row>
              <Col lg={5} md={6}>
                <ProductGallery gallery={detail?.info?.gallery} name={detail?.info?.name} htmlContent={detail?.info.detail} />
              </Col>
              <Col lg={7} md={6}>
                <div className='product-header'>
                  <span className='product-brand'>Brand: {detail?.info?.brandId}</span>
                  <h1 className='product-title'>{detail?.info?.name}</h1>
                  <div className='product-rating'>
                    <span className='stars'>
                      <FontAwesomeIcon icon={faStar} />
                      <FontAwesomeIcon icon={faStar} />
                      <FontAwesomeIcon icon={faStar} />
                      <FontAwesomeIcon icon={faStar} />
                      <FontAwesomeIcon icon={faStar} />
                    </span>
                    <span className='reviews-count'>4.8 (1000 reviews)</span>
                  </div>
                </div>

                <ProductPricing retailPrice={detail?.info?.retailPrice} counterPrice={detail?.info?.counterPrice} />

                <div className='product-options'>
                  <ProductOptions data={detail?.attribute} />
                </div>

                <form className='mt-3' onSubmit={handleSubmit}>
                  <RenderFormGroupField
                    input={{
                      name: 'quantity',
                      type: 'number',
                      placeholder: 'Quantity',
                      className: 'form-control',
                      value: quantity,
                      onChange: e => setQuantity(parseInt(e.target.value)),
                      min: '1',
                    }}
                    label='Quantity'
                    Icon={() => <FontAwesomeIcon icon={faShoppingCart} />}
                    meta={{
                      touched: false,
                      error: '',
                      warning: '',
                    }}
                  />
                </form>

                <div className='product-actions'>
                  <Button className='btn-buy-now' onClick={handleBuyNow}>
                    Buy Now
                  </Button>
                  <Button className='btn-add-to-cart' onClick={handleAddToCart}>
                    <FontAwesomeIcon icon={faShoppingCart} className='me-2' />
                    Add to Cart
                  </Button>
                </div>
              </Col>
            </Row>

            <div className='product-description mt-5'>
              <h3>Product Description</h3>
              <Tabs defaultActiveKey='description' id='product-tabs' className='mb-3'>
                <Tab eventKey='description' title='Description'>
                  <ProductInfo info={detail?.info} />
                </Tab>
                <Tab eventKey='specifications' title='Specifications'>
                  <ProductHighlights />
                </Tab>
                <Tab eventKey='reviews' title='Customer Reviews'>
                  <ProductDetailIssue data={detail?.issue} />
                </Tab>
                <Tab eventKey='details' title='Product Detail'>
                  <ProductDetailDetail detail={detail.info?.detail} />
                </Tab>
              </Tabs>
            </div>

            <Row className='mt-5'>
              <Col>
                <CardFeaturedProduct data={relatedGoods} onProductClick={scrollToGallery} />
              </Col>
            </Row>
          </Col>

          {/* <Col lg={2}>
            <SideCartSummary />
          </Col> */}
          {/* <Col lg={3}>
            {isAuthenticated ? (
              <SideCartSummary />
            ) : (
              <Card>
                <Card.Body>
                  <Button className='btn-buy-now mb-2' onClick={handleBuyNow}>
                    Buy Now
                  </Button>
                  <Button className='btn-add-to-cart' onClick={handleAddToCart}>
                    <FontAwesomeIcon icon={faShoppingCart} className='me-2' />
                    Add to Cart
                  </Button>
                </Card.Body>
              </Card>
            )}
          </Col> */}
        </Row>
        {/*         {showFloatingCart && <FloatingCartSidebar isOpen={showFloatingCart} onClose={toggleFloatingCart} />}
         */}{' '}
        <ToastContainer />
      </Container>
    </div>
  );
};

export default ProductDetailView;
