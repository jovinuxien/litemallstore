import { useAppSelector } from 'app/config/store';
import React from 'react';
import { Link } from 'react-router-dom';
import './FloatingCartSidebar.scss';

const FloatingCartSidebar = ({ isOpen, onClose }) => {
  const { cartList, cartTotal } = useAppSelector(state => state.cart.data);

  return (
    <div className={`floating-cart-sidebar ${isOpen ? 'open' : ''}`}>
      <button className='close-btn' onClick={onClose}>
        &times;
      </button>
      <h4>Cart Summary</h4>
      <ul>
        {cartList &&
          cartList.map(item => (
            <li key={item.id}>
              <span>{item.goodsName}</span>
              <span>${item.price * item.number}</span>
            </li>
          ))}
      </ul>
      <div className='cart-total'>
        <strong>Total: ${cartTotal?.checkedGoodsAmount}</strong>
      </div>
      <Link to='/cart' className='btn btn-primary'>
        View Cart
      </Link>
      <Link to='/checkout' className='btn btn-success'>
        Checkout
      </Link>
    </div>
  );
};

export default FloatingCartSidebar;
