import { useAppDispatch, useAppSelector } from 'app/config/store';
import { fetchCart, syncLocalCart, updateCartItem } from 'app/shared/reducers/cartSlice';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
/* const CouponApplyForm = lazy(() => import('../../components/others/CouponApplyForm'));
 */
const CartView = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [cartTotalItem, setCartTotalItem] = useState<number>(0);
  const { cartList, cartTotal } = useAppSelector(state => state.cart.data);
  const { isAuthenticated } = useAppSelector(state => state.auth.data);
  const [number, setNumber] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [showFloatingCart, setShowFloatingCart] = useState(false);

  const onSubmitApplyCouponCode = async values => {
    alert(JSON.stringify(values));
  };
  const handleDecreaseQuantity = (id: number) => {
    const item = cartList.find(item => item.id === id);
    if (item && item.number > 1) {
      const newQuantity = item.number - 1;
      updateCartItemQuantity(id, newQuantity);
    }
  };

  const toggleFloatingCart = () => {
    setShowFloatingCart(!showFloatingCart);
  };

  const addItemToCart = () => {};

  const handleUpdateQuantity = (id: number, newQuantity: number) => {
    const updatedCart = cartList.map(item => (item.id === id ? { ...item, number: newQuantity } : item));
    dispatch(syncLocalCart(updatedCart));
  };

  const handleAddToWishlist = (id: number) => {
    // Implement add to wishlist logic
  };

  const handleRemoveFromCart = (id: number) => {
    const updatedCart = cartList.filter(item => item.id !== id);
    dispatch(syncLocalCart(updatedCart));
  };
  const updateCartItemQuantity = (id: number, newQuantity: number) => {
    const updatedCart = cartList.map(item => (item.id === id ? { ...item, number: newQuantity } : item));

    if (isAuthenticated) {
      // If connected, update the server and then sync local cart
      dispatch(updateCartItem({ id, number: newQuantity })).then(() => dispatch(syncLocalCart(updatedCart)));
    } else {
      // If not connected, just update local cart
      dispatch(syncLocalCart(updatedCart));
    }
  };

  const handleContinueShopping = event => {
    event.preventDefault();
    navigate('/#hot-and-new');
    setTimeout(() => {
      const productsSection = document.getElementById('hot-and-new');
      if (productsSection) {
        productsSection.scrollIntoView({ behavior: 'smooth' });
      }
    }, 100);
  };

  useEffect(() => {
    const loadCart = async () => {
      try {
        await dispatch(fetchCart());
      } catch (error) {
        console.error('Error loading cart:', error);
      } finally {
        setIsLoading(false);
      }
    };
    loadCart();
  }, [dispatch]);

  useEffect(() => {
    if (cartList && cartList.length > 0) {
      dispatch(syncLocalCart(cartList));
    }
  }, [cartList, dispatch]);

  useEffect(() => {
    setCartTotalItem(cartList.length);
  }, [cartList]);

  if (isLoading) {
    return <div>Loading...</div>;
  }

  return (
    <div className={`main-content ${showFloatingCart ? 'sidebar-open' : ''}`}>
      <div className='bg-secondary border-top p-4  mb-3'>
        <h1 className='display-6'>Shopping Cart</h1>

        {cartList && cartList.length > 0 && (
          <p className='lead'>
            You have <b>{cartList.length}</b> items in your cart
          </p>
        )}
        {cartList.length === 0 && <p className='lead'>Your cart is empty</p>}
      </div>
      <div className='container mb-3'>
        <div className='row'>
          <div className='col-md-9'>
            <div className='card'>
              <div className='table-responsive'>
                <table className='table table-borderless'>
                  <thead className='text-muted'>
                    <tr className='small text-uppercase'>
                      <th scope='col'>Items</th>
                      <th scope='col' style={{ width: '15%' }}>
                        Quantity
                      </th>
                      <th scope='col' style={{ width: '15%' }}>
                        Price
                      </th>
                      <th scope='col' className='text-end' style={{ width: '15%' }}></th>
                    </tr>
                  </thead>
                  <tbody>
                    {cartList &&
                      cartList.map(item => (
                        <tr key={item.id}>
                          <td>
                            <div className='row'>
                              <div className='col-3 d-none d-md-block'>
                                <img src={item.picUrl} style={{ width: '100%', maxWidth: '80px' }} />
                              </div>
                              <div className='col'>
                                <Link to={`/product/${item.goodsId}`} className='text-decoration-none'>
                                  {item.goodsName}
                                </Link>
                                {/*                               <p className='small text-muted'>{item.specifications.join(', ')}</p>
                                 */}{' '}
                              </div>
                            </div>
                          </td>
                          <td>
                            <div className='input-group input-group-sm mw-140'>
                              <button className='btn btn-primary text-white' type='button' onClick={() => handleDecreaseQuantity(item.id)}>
                                <i className='bi bi-dash-lg'></i>
                              </button>
                              <input type='text' className='form-control' value={item.number} onChange={e => setNumber(parseInt(e.target.value))} readOnly />
                              <button className='btn btn-primary text-white' type='button' onClick={() => handleUpdateQuantity(item.id, item.number)}>
                                <i className='bi bi-plus-lg'></i>
                              </button>
                            </div>
                          </td>
                          <td>
                            <var className='price'>${item.price * item.number}</var>
                            <small className='d-block text-muted'>${item.price} each</small>
                          </td>
                          <td className='text-end'>
                            <button className='btn btn-sm btn-outline-secondary me-2' onClick={() => handleAddToWishlist(item.id)}>
                              <i className='bi bi-heart-fill'></i>
                            </button>
                            <button className='btn btn-sm btn-outline-danger' onClick={() => handleRemoveFromCart(item.id)}>
                              <i className='bi bi-trash'></i>
                            </button>
                          </td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
              <div className='card-footer'>
                <Link to='/checkout' className='btn btn-primary float-end'>
                  Make Purchase <i className='bi bi-chevron-right'></i>
                </Link>
                {/*  {cartList && cartList.length > 0 && (
                  <Link to={`/category/${cartList[0].id}`} className='btn btn-secondary'>
                    <i className='bi bi-chevron-left'></i> Continue shopping
                  </Link>
                )} */}
                <Link to={`/`} className='btn btn-secondary' onClick={handleContinueShopping}>
                  <i className='bi bi-chevron-left'></i> Continue shopping
                </Link>
              </div>
            </div>
            <div className='alert alert-success mt-3'>
              <p className='m-0'>
                <i className='bi bi-truck'></i> Free Delivery within 1-2 weeks
              </p>
            </div>
          </div>
          <div className='col-md-3'>
            <div className='card mb-3'>
              {/*  <div className='card-body'>
                <CouponApplyForm onSubmit={onSubmitApplyCouponCode} />
              </div> */}
            </div>
            <div className='card'>
              <div className='card-body'>
                <dl className='row border-bottom'>
                  <dt className='col-6'>Total price:</dt>
                  <dd className='col-6 text-end'>${cartTotal?.goodsAmount}</dd>

                  <dt className='col-6 text-success'>Discount:</dt>
                  <dd className='col-6 text-success text-end'>-$0.00</dd>
                  <dt className='col-6 text-success'>
                    Coupon: <span className='small text-muted'>N/A</span>{' '}
                  </dt>
                  <dd className='col-6 text-success text-end'>-$0.00</dd>
                </dl>

                <dl className='row'>
                  <dt className='col-6'>Total:</dt>
                  <dd className='col-6 text-end  h5'>
                    <strong>${cartTotal?.checkedGoodsAmount}</strong>
                  </dd>
                </dl>
                <hr />
                <p className='text-center'>
                  <img src='../../images/payment/payments.webp' alt='...' height={26} />
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div className='bg-light border-top p-4'>
        <div className='container'>
          <h6>Payment and refund policy</h6>
          <p>
            Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim
            veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit
            esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est
            laborum.
          </p>
          <p>
            Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim
            veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit
            esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est
            laborum.
          </p>
        </div>
      </div>
      {/*       <FloatingCartSidebar isOpen={showFloatingCart} onClose={toggleFloatingCart} />
       */}{' '}
    </div>
  );
};

export default CartView;
