import React, { useEffect, useState } from 'react';
import { Button, Col, Container, Image, Row, Spinner } from 'react-bootstrap';
import { useNavigate, useParams } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { addItem } from 'app/shared/reducers/cartSlice';
import { getProductDetail } from './productDetailSlice';

const priceNum = (price: unknown): number => {
  if (price == null) return 0;
  if (typeof price === 'number') return price;
  if (typeof price === 'object' && 'amount' in (price as Record<string, unknown>)) {
    return Number((price as { amount: unknown }).amount) || 0;
  }
  const n = Number(price);
  return Number.isFinite(n) ? n : 0;
};

const ProductDetailView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();

  const { data, loading } = useAppSelector(state => state.productDetail);
  const { info, productList, attribute } = data;

  const [quantity, setQuantity] = useState(1);
  const [activeImage, setActiveImage] = useState<string>('');

  useEffect(() => {
    if (id) dispatch(getProductDetail(Number(id)));
  }, [dispatch, id]);

  useEffect(() => {
    setActiveImage(info?.picUrl ?? '');
  }, [info?.picUrl]);

  if (loading === 'pending') {
    return (
      <Container className='my-5 text-center'>
        <Spinner animation='border' />
      </Container>
    );
  }

  if (!info || !info.id) {
    return <Container className='my-5'>Product not found.</Container>;
  }

  const retail = priceNum(info.retailPrice);
  const counter = priceNum(info.counterPrice);
  const gallery = info.gallery?.length ? info.gallery : info.picUrl ? [info.picUrl] : [];

  const handleAddToCart = () => {
    const defaultSku = productList?.[0];
    dispatch(
      addItem({
        id: defaultSku?.id ?? info.id,
        goodsId: String(info.id),
        goodsName: info.name,
        productId: defaultSku?.id,
        price: defaultSku?.price ?? retail,
        number: quantity,
        picUrl: info.picUrl,
        specifications: [],
        checked: true,
      })
    );
    navigate('/cart');
  };

  return (
    <Container className='my-4'>
      <Row>
        <Col md={6}>
          <Image src={activeImage} fluid className='border rounded mb-2' style={{ maxHeight: '420px', objectFit: 'contain', width: '100%' }} />
          <div className='d-flex gap-2 flex-wrap'>
            {gallery.map((img, i) => (
              <Image
                key={i}
                src={img}
                thumbnail
                role='button'
                style={{ width: '64px', height: '64px', objectFit: 'cover' }}
                onClick={() => setActiveImage(img)}
              />
            ))}
          </div>
        </Col>
        <Col md={6}>
          <h2>{info.name}</h2>
          <p className='text-muted'>{info.brief}</p>
          <div className='mb-3'>
            <span className='h3 text-primary me-2'>${retail}</span>
            {counter > retail && <span className='text-muted text-decoration-line-through'>${counter}</span>}
          </div>

          <div className='d-flex align-items-center mb-3'>
            <label className='me-2'>Quantity</label>
            <input
              type='number'
              min={1}
              className='form-control'
              style={{ width: '90px' }}
              value={quantity}
              onChange={e => setQuantity(Math.max(1, parseInt(e.target.value, 10) || 1))}
            />
          </div>

          <Button variant='primary' onClick={handleAddToCart}>
            <i className='bi bi-cart-plus me-1' /> Add to cart
          </Button>

          {attribute && attribute.length > 0 && (
            <table className='table table-sm mt-4'>
              <tbody>
                {attribute.map(attr => (
                  <tr key={attr.id}>
                    <th className='text-muted'>{attr.attribute}</th>
                    <td>{attr.value}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Col>
      </Row>

      {info.detail && (
        <Row className='mt-4'>
          <Col>
            <h4>Details</h4>
            <div dangerouslySetInnerHTML={{ __html: info.detail }} />
          </Col>
        </Row>
      )}
    </Container>
  );
};

export default ProductDetailView;
