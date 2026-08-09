import StripePaymentComponent from 'app/components/userComponents/card/StripeComponent';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import renderFormField from 'app/helpers/renderFormField';
import renderFormSelect from 'app/helpers/renderFormSelect';
import { fetchCart } from 'app/shared/reducers/cartSlice';
import React, { useEffect, useState } from 'react';
import './Checkout.scss';
const CheckoutView = () => {
  const dispatch = useAppDispatch();
  const [isPaymentReady, setIsPaymentReady] = useState(false);

  const [formState, setFormState] = useState({
    email: '',
    mobile: '',
    name: '',
    address: '',
    addressTwo: '',
  });
  const [region, setRegion] = useState<string>('');
  const [kommune, setKommune] = useState<string>('');

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setFormState(prevState => ({
      ...prevState,
      [name]: value,
    }));
  };

  const handleRegionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setRegion(e.target.value);
  };

  const handleKommuneChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setRegion(e.target.value);
  };

  const handleSubmit = (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    // Handling form submission
    console.log(formState);
  };

  const { cartList, cartTotal } = useAppSelector(state => state.cart.data);

  useEffect(() => {
    dispatch(fetchCart());
  }, [dispatch]);

  const handleProceedToPayment = () => {
    setIsPaymentReady(true);
  };
  return (
    <div>
      <div className='bg-secondary border-top p-4  mb-3'>
        <h1 className='display-6'>Checkout</h1>
      </div>
      <div className='container mb-3'>
        <div className='row'>
          <div className='col-md-8'>
            <form onSubmit={handleSubmit}>
              <div className='card mb-3 border-info'>
                <div className='card-header bg-info'>
                  <i className='bi bi-envelope'></i> Contact Info
                </div>
                <div className='card-body'>
                  <div className='row g-3'>
                    <div className='col-md-6'>
                      {renderFormField({
                        input: {
                          name: 'fullName',
                          value: formState.email,
                          onChange: handleInputChange,
                        },
                        label: 'Email Address',
                        required: true,
                        tips: '',
                        type: 'text',
                        meta: { touched: true, error: undefined, warning: undefined },
                        id: '',
                      })}
                    </div>
                    <div className='col-md-6'>
                      {renderFormField({
                        input: {
                          name: 'Mobile no',
                          value: formState.mobile,
                          onChange: handleInputChange,
                        },
                        label: 'Mobile No',
                        required: true,
                        tips: '',
                        type: 'text',
                        meta: { touched: true, error: undefined, warning: undefined },
                        id: '',
                      })}
                    </div>
                  </div>
                </div>
              </div>

              <div className='card mb-3 border-info'>
                <div className='card-header bg-info'>
                  <i className='bi bi-truck'></i> Shipping Information
                </div>
                <div className='card-body'>
                  <div className='row g-3'>
                    <div className='col-md-12'>
                      {renderFormField({
                        input: {
                          name: 'Name',
                          value: formState.name,
                          onChange: handleInputChange,
                        },
                        label: 'Full Name',
                        required: true,
                        tips: '',
                        type: 'text',
                        meta: { touched: true, error: undefined, warning: undefined },
                        id: '',
                      })}
                    </div>
                    <div className='col-md-6'>
                      {renderFormField({
                        input: {
                          name: 'address',
                          value: formState.address,
                          onChange: handleInputChange,
                        },
                        label: 'Address Line 1',
                        required: true,
                        tips: '',
                        type: 'text',
                        meta: { touched: true, error: undefined, warning: undefined },
                        id: '',
                      })}
                    </div>
                    <div className='col-md-6'>
                      {renderFormField({
                        input: {
                          name: 'addressTwo',
                          value: formState.addressTwo,
                          onChange: handleInputChange,
                        },
                        label: 'Address Line 2 (optional)',
                        required: false,
                        tips: '',
                        type: 'text',
                        meta: { touched: true, error: undefined, warning: undefined },
                        id: '',
                      })}
                    </div>
                    <div className='col-md-4'>
                      {renderFormSelect({
                        input: {
                          name: 'region',
                          value: region,
                          onChange: handleRegionChange,
                        },
                        label: 'Region',
                        options: [
                          { value: '', label: '-- Region --' },
                          { value: 'Stockholm', label: 'Stockholm' },
                          { value: 'Skone', label: 'Skone' },
                          { value: 'Stockholm', label: 'Stockholm' },
                        ],
                        required: true,
                        meta: { touched: true, error: undefined, warning: undefined },
                      })}
                    </div>

                    <div className='col-md-4'>
                      <input type='text' className='form-control' placeholder='Zip' required />
                    </div>
                  </div>
                </div>
              </div>

              <div className='card mb-3 border-info'>
                <div className='card-header bg-info'>
                  <i className='bi bi-credit-card-2-front'></i> Payment Method
                </div>
                <div className='card-body'>
                  <StripePaymentComponent amount={cartTotal?.goodsAmount * 100} currency='usd' />
                </div>
              </div>
              {!isPaymentReady && (
                <form
                  onSubmit={e => {
                    e.preventDefault();
                    handleProceedToPayment();
                  }}
                >
                  {/* Add your shipping and billing form fields here */}
                  <button type='submit' className='btn btn-info'>
                    Pay Now <strong>{cartTotal?.goodsAmount}</strong>
                  </button>
                </form>
              )}
            </form>

            {/* Stripe Payment Component */}
            {isPaymentReady && <StripePaymentComponent amount={cartTotal?.goodsAmount * 100} currency='usd' />}
          </div>
          {/* Cart Summary elements section */}
          <div className='col-md-4'>
            <div className='card'>
              <div className='card-header bg-info'>
                <i className='bi bi-cart3'></i> Cart <span className='badge bg-secondary float-end'>{cartList?.length}</span>
              </div>
              <ul className='list-group list-group-flush'>
                {cartList.map(item => (
                  <li key={item.id} className='list-group-item d-flex justify-content-between lh-sm'>
                    <div className='d-flex align-items-center'>
                      <img
                        src={item.picUrl}
                        alt={item.goodsName}
                        className='img-thumbnail me-3'
                        style={{ width: '50px', height: '50px', objectFit: 'cover' }}
                      />
                      <div>
                        <h6 className='my-0'>{item.goodsName.length > 25 ? item.goodsName.substring(0, 23) + '...' : item.goodsName}</h6>
                        <small className='text-muted'>
                          {item.specifications && item.specifications.length > 0 ? (
                            <span className='badge bg-light text-dark'>{item.specifications.join(', ')}</span>
                          ) : (
                            <span className='text-secondary'>No specifications</span>
                          )}
                        </small>
                      </div>
                    </div>
                    <span className='text-muted'>${(item.price * item.number).toFixed(2)}</span>
                  </li>
                ))}
                <li className='list-group-item d-flex justify-content-between'>
                  <span>Total (EUR)</span>
                  <strong>${cartTotal?.checkedGoodsAmount}</strong>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CheckoutView;
