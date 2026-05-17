import { IRelatedGood } from 'app/shared/model/product/product.model';
import React from 'react';
import { Card, Col, Container, Row } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

type CardFeaturedProductProps = {
  data: IRelatedGood;
  onProductClick: () => void;
};

const CardFeaturedProduct: React.FC<CardFeaturedProductProps> = ({ data, onProductClick }) => {
  const navigate = useNavigate();
  const goodss = data.list.slice(0, 6);
  const handleProductClick = (productId: number) => {
    // Navigate to product detail page
    navigate(`/product/${productId}`);
    window.scrollTo(0, 0);
    onProductClick();
  };
  return (
    <Container className='container-fluid mt-4'>
      <Row className=''>
        {goodss.map((good, idx) => (
          <Col key={idx} xs={12} md={3} className='mb-4'>
            <Link
              to={`/product/${good.id}`}
              className='text-decoration-none'
              onClick={e => {
                e.preventDefault();
                handleProductClick(good.id);
              }}
            >
              <Card className='h-100 deal-card shadow-sm'>
                <div className='image-container'>
                  <Card.Img variant='top' src={good.picUrl} className='product-image' />
                  <div className='badge-container'>
                    {good.isNew && <div className='badge badge-new'>New</div>}
                    {good.isHot && <div className='badge badge-hot'>Hot</div>}
                  </div>
                </div>
                <Card.Body className='d-flex flex-column'>
                  <Card.Title
                    className='product-title h6 mb-2'
                    style={{ fontSize: '14px', fontWeight: 'bold', lineHeight: '1.2', fontFamily: 'Helvetica Neue, Arial, sans-serif' }}
                  >
                    {good.name}
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
                    {good.brief}
                  </Card.Text>
                  <div className='mt-auto'>
                    <div className='d-flex flex-wrap align-items-end justify-content-between'>
                      <div className='price-container flex-grow-1 me-2 mb-2'>
                        <div className='d-flex align-items-baseline'>
                          <span className='current-price me-2' style={{ fontSize: '1.1rem', fontWeight: 'bold', color: '#e53935' }}>
                            ${good.retailPrice}
                          </span>
                          <span className='original-price text-muted' style={{ fontSize: '0.9rem', textDecoration: 'line-through' }}>
                            ${good.counterPrice}
                          </span>
                        </div>
                        <div className='discount-badge'>{Math.round(((good.counterPrice - good.retailPrice) / good.counterPrice) * 100)}% OFF</div>
                      </div>
                      {/*  <button className='btn btn-primary btn-sm view-deal-button'>Detail</button> */}
                    </div>
                  </div>
                </Card.Body>
              </Card>
            </Link>
          </Col>
        ))}
      </Row>
    </Container>
  );
};

export default CardFeaturedProduct;
