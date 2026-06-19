import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Form, Row, Spinner } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import { CheckoutPaymentMethod, placeOrder, resetOrderState, ShippingInfo } from 'app/shared/reducers/orderSlice';
import { IAddress, ICoupon, isMissingEndpoint, userApi } from 'app/shared/api';
import './Checkout.scss';

type Step = 'review' | 'address' | 'payment';

const REGIONS = ['Stockholm', 'Skåne', 'Göteborg', 'Uppsala'];

const EMPTY_SHIPPING: ShippingInfo = {
  name: '',
  mobile: '',
  email: '',
  address: '',
  addressTwo: '',
  region: '',
  kommune: '',
  zip: '',
};

/** Map a saved address-book entry onto the order's ShippingInfo submit shape. */
const addressToShipping = (a: IAddress): ShippingInfo => ({
  name: a.name ?? '',
  mobile: a.tel ?? '',
  email: '',
  address: a.addressDetail ?? '',
  addressTwo: '',
  region: a.province ?? '',
  kommune: a.city ?? a.county ?? '',
  zip: a.postalCode ?? '',
});

/**
 * Customer checkout, modelled on litemall-vue's `order/checkout`: cart review →
 * address selection (saved address book, with a new-address fallback) → coupon
 * selection → order summary (goods + freight − coupon) → payment method →
 * place order → confirmation. Submits through `placeOrder` (/srv/order/submit).
 *
 * Address book and coupons come from the `/srv` api seam; if those endpoints
 * aren't live yet the flow degrades to the manual address form and no coupons,
 * so checkout still completes.
 */
const CheckoutView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const { loading: orderLoading, errorMessage: orderError } = useAppSelector(state => state.order);

  const [step, setStep] = useState<Step>('review');
  const [shipping, setShipping] = useState<ShippingInfo>(EMPTY_SHIPPING);
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');

  // Address book (graceful when /srv/address isn't live yet).
  const [addresses, setAddresses] = useState<IAddress[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | 'new' | null>(null);

  // Coupons (graceful when /srv/coupon isn't live yet).
  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);

  useEffect(() => {
    dispatch(fetchCart());
    dispatch(resetOrderState());
    userApi
      .addressList()
      .then(list => {
        const arr = list ?? [];
        setAddresses(arr);
        const def = arr.find(a => a.isDefault) ?? arr[0];
        if (def?.id != null) {
          setSelectedAddressId(def.id);
          setShipping(addressToShipping(def));
        } else {
          setSelectedAddressId('new');
        }
      })
      .catch(e => {
        if (isMissingEndpoint(e)) setSelectedAddressId('new');
      });
    userApi
      .couponMyList(1)
      .then(res => setCoupons(res?.list ?? []))
      .catch(() => setCoupons([]));
  }, [dispatch]);

  const cartTotalAmount = useMemo(
    () => cartList.reduce((sum, item) => sum + (item.price ?? 0) * (item.number ?? 0), 0),
    [cartList]
  );

  const selectedCoupon = useMemo(() => coupons.find(c => c.id === selectedCouponId) ?? null, [coupons, selectedCouponId]);
  const couponDiscount = useMemo(() => {
    if (!selectedCoupon) return 0;
    if ((selectedCoupon.min ?? 0) > cartTotalAmount) return 0;
    return Math.min(selectedCoupon.discount ?? 0, cartTotalAmount);
  }, [selectedCoupon, cartTotalAmount]);
  const grandTotal = Math.max(0, cartTotalAmount - couponDiscount);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setShipping(prev => ({ ...prev, [name]: value }));
  };
  const handleRegionChange = (e: React.ChangeEvent<HTMLSelectElement>) => setShipping(prev => ({ ...prev, region: e.target.value }));
  const handleKommuneChange = (e: React.ChangeEvent<HTMLSelectElement>) => setShipping(prev => ({ ...prev, kommune: e.target.value }));

  const pickAddress = (a: IAddress) => {
    setSelectedAddressId(a.id ?? 'new');
    setShipping(addressToShipping(a));
  };

  const usingNewAddress = selectedAddressId === 'new' || addresses.length === 0;
  // Saved address is pre-validated; a new address needs the core fields.
  const addressValid = usingNewAddress ? !!(shipping.name && shipping.address && shipping.region && shipping.zip) : selectedAddressId != null;

  // The order submit binds a saved-address id (LitemallPlaceOrderCommand.addressId);
  // it cannot take the inline form. Until `/srv/address/save` lands (order/user
  // follow-up, docs/SRV-FOLLOWUPS.md) only a picked saved address can be submitted.
  const savedAddressId = typeof selectedAddressId === 'number' ? selectedAddressId : null;

  const handlePlaceOrder = async () => {
    if (savedAddressId == null) return; // guarded by the disabled button below
    const result = await dispatch(
      placeOrder({
        cartId: 0, // 0 == checkout the whole checked cart
        addressId: savedAddressId,
        userCouponId: selectedCouponId ?? undefined,
        paymentMethod,
      })
    );
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
          {(['review', 'address', 'payment'] as Step[]).map((s, i) => (
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
                  <Button variant='primary' onClick={() => setStep('address')}>
                    Continue to address
                  </Button>
                </Card.Footer>
              </Card>
            )}

            {step === 'address' && (
              <Card className='mb-3'>
                <Card.Header>Delivery address</Card.Header>
                <Card.Body>
                  {addresses.length > 0 && (
                    <div className='mb-3 d-grid gap-2'>
                      {addresses.map(a => (
                        <button
                          key={a.id}
                          type='button'
                          className={`lm-addr-card${selectedAddressId === a.id ? ' is-active' : ''}`}
                          onClick={() => pickAddress(a)}
                        >
                          <div className='lm-addr-card__head'>
                            <strong>{a.name}</strong> <span className='text-muted'>{a.tel}</span>
                            {a.isDefault && <span className='badge bg-primary ms-2'>Default</span>}
                          </div>
                          <div className='text-muted'>
                            {[a.province, a.city, a.county, a.addressDetail].filter(Boolean).join(' ')}
                          </div>
                        </button>
                      ))}
                      <button
                        type='button'
                        className={`lm-addr-card lm-addr-card--new${selectedAddressId === 'new' ? ' is-active' : ''}`}
                        onClick={() => {
                          setSelectedAddressId('new');
                          setShipping(EMPTY_SHIPPING);
                        }}
                      >
                        <i className='bi bi-plus-lg' /> Use a new address
                      </button>
                    </div>
                  )}

                  {usingNewAddress && (
                    <Row className='g-3'>
                      <Col md={6}>
                        <Form.Label>Full name *</Form.Label>
                        <Form.Control name='name' value={shipping.name} onChange={handleInputChange} required />
                      </Col>
                      <Col md={6}>
                        <Form.Label>Mobile</Form.Label>
                        <Form.Control name='mobile' value={shipping.mobile} onChange={handleInputChange} />
                      </Col>
                      <Col md={12}>
                        <Form.Label>Email</Form.Label>
                        <Form.Control name='email' type='email' value={shipping.email} onChange={handleInputChange} />
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
                  )}
                </Card.Body>
                <Card.Footer className='d-flex justify-content-between'>
                  <Button variant='outline-secondary' onClick={() => setStep('review')}>
                    Back
                  </Button>
                  <Button variant='primary' disabled={!addressValid} onClick={() => setStep('payment')}>
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

                  {savedAddressId == null && (
                    <Alert variant='warning' className='mt-3'>
                      Placing an order needs a saved delivery address. The address book
                      (<code>/srv/address</code>) is a pending order/user backend follow-up, so a
                      manually-typed address cannot be submitted yet.
                    </Alert>
                  )}

                  {orderError && (
                    <Alert variant='danger' className='mt-3'>
                      {orderError}
                    </Alert>
                  )}
                </Card.Body>
                <Card.Footer className='d-flex justify-content-between'>
                  <Button variant='outline-secondary' onClick={() => setStep('address')} disabled={orderLoading === 'pending'}>
                    Back
                  </Button>
                  <Button variant='success' onClick={handlePlaceOrder} disabled={orderLoading === 'pending' || savedAddressId == null}>
                    {orderLoading === 'pending' ? (
                      <>
                        <Spinner animation='border' size='sm' className='me-2' />
                        Placing order…
                      </>
                    ) : (
                      <>Place order — ${grandTotal.toFixed(2)}</>
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

                {coupons.length > 0 && (
                  <li className='list-group-item'>
                    <Form.Label className='small text-muted mb-1'>Coupon</Form.Label>
                    <Form.Select
                      size='sm'
                      value={selectedCouponId ?? ''}
                      onChange={e => setSelectedCouponId(e.target.value ? Number(e.target.value) : null)}
                    >
                      <option value=''>No coupon</option>
                      {coupons.map(c => (
                        <option key={c.id} value={c.id} disabled={(c.min ?? 0) > cartTotalAmount}>
                          −${c.discount} {c.min ? `(over $${c.min})` : ''}
                        </option>
                      ))}
                    </Form.Select>
                  </li>
                )}

                <li className='list-group-item d-flex justify-content-between'>
                  <span>Subtotal</span>
                  <span>${cartTotalAmount.toFixed(2)}</span>
                </li>
                {couponDiscount > 0 && (
                  <li className='list-group-item d-flex justify-content-between text-success'>
                    <span>Coupon</span>
                    <span>−${couponDiscount.toFixed(2)}</span>
                  </li>
                )}
                <li className='list-group-item d-flex justify-content-between'>
                  <span>Total (USD)</span>
                  <strong>${grandTotal.toFixed(2)}</strong>
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
