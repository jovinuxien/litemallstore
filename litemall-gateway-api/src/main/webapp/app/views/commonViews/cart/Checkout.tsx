import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, Row, Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import { CheckoutPaymentMethod, placeOrder, resetOrderState, ShippingInfo } from 'app/shared/reducers/orderSlice';
import './Checkout.scss';

type Step = 'review' | 'shipping' | 'payment';

const REGIONS = ['Stockholm', 'Skåne', 'Göteborg', 'Uppsala'];

// CJ createOrder needs a real destination country + ISO code. Small built-in list;
// the selected option supplies both the country name and the countryCode.
const COUNTRIES: Array<{ name: string; code: string }> = [
  { name: 'United States', code: 'US' },
  { name: 'United Kingdom', code: 'GB' },
  { name: 'Sweden', code: 'SE' },
  { name: 'Norway', code: 'NO' },
  { name: 'Germany', code: 'DE' },
  { name: 'France', code: 'FR' },
  { name: 'Canada', code: 'CA' },
  { name: 'Australia', code: 'AU' },
];

const isCjItem = (it: { source?: string; goodsId?: string }) =>
  it.source === 'cj_dropshipping' || String(it.goodsId ?? '').startsWith('cj_');

const CheckoutView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const { loading: orderLoading, errorMessage: orderError } = useAppSelector(state => state.order);

  const [step, setStep] = useState<Step>('review');
  const [shipping, setShipping] = useState<ShippingInfo>({
    name: '',
    mobile: '',
    email: '',
    address: '',
    addressTwo: '',
    region: '',
    kommune: '',
    zip: '',
    country: '',
    countryCode: '',
  });
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');

  // CJ lines ship via the CJ dropship endpoint, which requires country + phone.
  const hasCjItems = useMemo(() => cartList.some(isCjItem), [cartList]);

  useEffect(() => {
    dispatch(fetchCart());
    dispatch(resetOrderState());
  }, [dispatch]);

  const cartTotalAmount = useMemo(
    () => cartList.reduce((sum, item) => sum + (item.price ?? 0) * (item.number ?? 0), 0),
    [cartList]
  );

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setShipping(prev => ({ ...prev, [name]: value }));
  };

  const handleRegionChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setShipping(prev => ({ ...prev, region: e.target.value }));
  };

  // Fixed: the kommune handler used to overwrite `region`.
  const handleKommuneChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    setShipping(prev => ({ ...prev, kommune: e.target.value }));
  };

  const handleCountryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const code = e.target.value;
    const country = COUNTRIES.find(c => c.code === code)?.name ?? '';
    setShipping(prev => ({ ...prev, countryCode: code, country }));
  };

  const baseValid = shipping.name && shipping.email && shipping.address && shipping.region && shipping.zip;
  // CJ orders additionally need a phone and a destination country.
  const shippingValid = baseValid && (!hasCjItems || (shipping.mobile && shipping.countryCode));

  const handlePlaceOrder = async () => {
    const result = await dispatch(placeOrder({ items: cartList, shipping, paymentMethod }));
    if (placeOrder.fulfilled.match(result)) {
      dispatch(clearCart());
      navigate(`/order-confirmation/${result.payload.orderId}`);
    }
    // On rejection the error is shown from order state; cart is untouched.
  };

  // Empty-cart guard.
  if (cartList.length === 0) {
    return (
      <div className='container my-5 text-center'>
        <h2 className='display-6'>Your cart is empty</h2>
        <p className='text-muted'>Add some products before checking out.</p>
        <Link to='/' className='btn btn-primary'>
          Continue shopping
        </Link>
      </div>
    );
  }

  return (
    <div>
      <div className='bg-secondary border-top p-4 mb-3'>
        <h1 className='display-6'>Checkout</h1>
      </div>
      <div className='container mb-5'>
        {/* Step indicator */}
        <div className='d-flex gap-3 mb-4'>
          {(['review', 'shipping', 'payment'] as Step[]).map((s, i) => (
            <span key={s} className={`badge ${step === s ? 'bg-primary' : 'bg-light text-dark'}`}>
              {i + 1}. {s.charAt(0).toUpperCase() + s.slice(1)}
            </span>
          ))}
        </div>

        <Row>
          <Col md={8}>
            {step === 'review' && (
              <Card className='mb-3'>
                <Card.Header>Review your items</Card.Header>
                <ul className='list-group list-group-flush'>
                  {cartList.map(item => (
                    <li key={item.id} className='list-group-item d-flex justify-content-between align-items-center'>
                      <div className='d-flex align-items-center'>
                        <img src={item.picUrl} alt={item.goodsName} style={{ width: '48px', height: '48px', objectFit: 'cover' }} className='me-3' />
                        <div>
                          <div>{item.goodsName}</div>
                          <small className='text-muted'>Qty {item.number}</small>
                        </div>
                      </div>
                      <span>${((item.price ?? 0) * (item.number ?? 0)).toFixed(2)}</span>
                    </li>
                  ))}
                </ul>
                <Card.Footer className='text-end'>
                  <Button variant='primary' onClick={() => setStep('shipping')}>
                    Continue to shipping
                  </Button>
                </Card.Footer>
              </Card>
            )}

            {step === 'shipping' && (
              <Card className='mb-3'>
                <Card.Header>Shipping information</Card.Header>
                <Card.Body>
                  {hasCjItems && (
                    <Alert variant='info' className='mb-3'>
                      Some items ship via <strong>CJ Dropshipping</strong> — please provide a <strong>country</strong> and a <strong>phone number</strong> for delivery.
                    </Alert>
                  )}
                  <Row className='g-3'>
                    <Col md={6}>
                      <Form.Label>Full name *</Form.Label>
                      <Form.Control name='name' value={shipping.name} onChange={handleInputChange} required />
                    </Col>
                    <Col md={6}>
                      <Form.Label>Email *</Form.Label>
                      <Form.Control name='email' type='email' value={shipping.email} onChange={handleInputChange} required />
                    </Col>
                    <Col md={6}>
                      <Form.Label>Mobile{hasCjItems ? ' *' : ''}</Form.Label>
                      <Form.Control name='mobile' value={shipping.mobile} onChange={handleInputChange} required={hasCjItems} />
                    </Col>
                    <Col md={6}>
                      <Form.Label>Country{hasCjItems ? ' *' : ''}</Form.Label>
                      <Form.Select name='countryCode' value={shipping.countryCode} onChange={handleCountryChange} required={hasCjItems}>
                        <option value=''>-- Country --</option>
                        {COUNTRIES.map(c => (
                          <option key={c.code} value={c.code}>
                            {c.name}
                          </option>
                        ))}
                      </Form.Select>
                    </Col>
                    <Col md={12}>
                      <Form.Label>Address line 1 *</Form.Label>
                      <Form.Control name='address' value={shipping.address} onChange={handleInputChange} required />
                    </Col>
                    <Col md={12}>
                      <Form.Label>Address line 2</Form.Label>
                      <Form.Control name='addressTwo' value={shipping.addressTwo} onChange={handleInputChange} />
                    </Col>
                    <Col md={4}>
                      <Form.Label>Region *</Form.Label>
                      <Form.Select name='region' value={shipping.region} onChange={handleRegionChange} required>
                        <option value=''>-- Region --</option>
                        {REGIONS.map(r => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </Form.Select>
                    </Col>
                    <Col md={4}>
                      <Form.Label>Kommune</Form.Label>
                      <Form.Select name='kommune' value={shipping.kommune} onChange={handleKommuneChange}>
                        <option value=''>-- Kommune --</option>
                        {REGIONS.map(r => (
                          <option key={r} value={r}>
                            {r}
                          </option>
                        ))}
                      </Form.Select>
                    </Col>
                    <Col md={4}>
                      <Form.Label>Zip *</Form.Label>
                      <Form.Control name='zip' value={shipping.zip} onChange={handleInputChange} required />
                    </Col>
                  </Row>
                </Card.Body>
                <Card.Footer className='d-flex justify-content-between'>
                  <Button variant='outline-secondary' onClick={() => setStep('review')}>
                    Back
                  </Button>
                  <Button variant='primary' disabled={!shippingValid} onClick={() => setStep('payment')}>
                    Continue to payment
                  </Button>
                </Card.Footer>
              </Card>
            )}

            {step === 'payment' && (
              <Card className='mb-3'>
                <Card.Header>Payment method</Card.Header>
                <Card.Body>
                  <Form.Check
                    type='radio'
                    id='pay-card'
                    name='paymentMethod'
                    label='Credit / debit card'
                    checked={paymentMethod === 'CARD'}
                    onChange={() => setPaymentMethod('CARD')}
                  />
                  <Form.Check
                    type='radio'
                    id='pay-wallet'
                    name='paymentMethod'
                    label='Digital wallet (balance)'
                    checked={paymentMethod === 'WALLET'}
                    onChange={() => setPaymentMethod('WALLET')}
                  />

                  {paymentMethod === 'CARD' && (
                    <Alert variant='light' className='mt-3 border'>
                      Card payment is handled on the confirmation step (Stripe). No card data is collected here.
                    </Alert>
                  )}
                  {paymentMethod === 'WALLET' && (
                    <Alert variant='light' className='mt-3 border'>
                      Your wallet balance will be debited when the order is placed. Insufficient balance will cancel the order.
                    </Alert>
                  )}

                  {orderError && <Alert variant='danger' className='mt-3'>{orderError}</Alert>}
                </Card.Body>
                <Card.Footer className='d-flex justify-content-between'>
                  <Button variant='outline-secondary' onClick={() => setStep('shipping')} disabled={orderLoading === 'pending'}>
                    Back
                  </Button>
                  <Button variant='success' onClick={handlePlaceOrder} disabled={orderLoading === 'pending'}>
                    {orderLoading === 'pending' ? (
                      <>
                        <Spinner animation='border' size='sm' className='me-2' />
                        Placing order…
                      </>
                    ) : (
                      <>Place order — ${cartTotalAmount.toFixed(2)}</>
                    )}
                  </Button>
                </Card.Footer>
              </Card>
            )}
          </Col>

          {/* Order summary */}
          <Col md={4}>
            <Card>
              <Card.Header>
                <i className='bi bi-cart3' /> Cart <span className='badge bg-secondary float-end'>{cartList.length}</span>
              </Card.Header>
              <ul className='list-group list-group-flush'>
                {cartList.map(item => (
                  <li key={item.id} className='list-group-item d-flex justify-content-between lh-sm'>
                    <div>
                      <h6 className='my-0'>{(item.goodsName ?? '').length > 25 ? (item.goodsName ?? '').substring(0, 23) + '…' : item.goodsName}</h6>
                      <small className='text-muted'>Qty {item.number}</small>
                    </div>
                    <span className='text-muted'>${((item.price ?? 0) * (item.number ?? 0)).toFixed(2)}</span>
                  </li>
                ))}
                <li className='list-group-item d-flex justify-content-between'>
                  <span>Total (USD)</span>
                  <strong>${cartTotalAmount.toFixed(2)}</strong>
                </li>
              </ul>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  );
};

export default CheckoutView;
