import { faShoppingCart } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useAppSelector } from 'app/config/hooks';
import React from 'react';
import { Button, Card, Image } from 'react-bootstrap';
import { Link } from 'react-router-dom';
import './SideCartSummary.scss';

const SideCartSummary: React.FC = () => {
  const { cartList, cartTotal } = useAppSelector(state => state.cart.data);

  return (
    <Card className='side-cart-summary'>
      <Card.Body>
        <Card.Title className='mb-3'>Cart Summary</Card.Title>
        <div className='cart-items-preview'>
          {cartList.slice(0, 3).map((item, index) => (
            <Image key={index} src={item.picUrl} alt={item.goodsName} thumbnail className='cart-item-thumbnail me-2' />
          ))}
          {cartList.length > 3 && <span>+{cartList.length - 3} more</span>}
        </div>
        <Card.Text className='mt-2'>{cartList.length} item(s) in cart</Card.Text>
        <Card.Text className='fw-bold'>Total: $ {cartTotal.goodsAmount}</Card.Text>
        <Link to='/cart'>
          <Button variant='primary' className='w-100'>
            <FontAwesomeIcon icon={faShoppingCart} className='me-2' />
            View Cart & Checkout
          </Button>
        </Link>
      </Card.Body>
    </Card>
  );
};

export default SideCartSummary;
