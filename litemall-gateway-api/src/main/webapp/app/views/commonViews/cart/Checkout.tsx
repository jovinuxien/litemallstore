import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Form } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import { CheckoutPaymentMethod, payOrder, placeOrder, resetOrderState, ShippingInfo } from 'app/shared/reducers/orderSlice';
import { IAddress, ICoupon, isMissingEndpoint, userApi } from 'app/shared/api';
import {
  Cell,
  CellGroup,
  EmptyState,
  GoodsLineCard,
  OrderSummary,
  Page,
  PageHead,
  SubmitBar,
  AddressCard,
} from 'app/components/commonComponents/storefront';

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

/** Map a typed checkout address onto the AddressSaveRequest/IAddress shape. */
const shippingToAddress = (s: ShippingInfo): IAddress => ({
  name: s.name,
  tel: s.mobile,
  province: s.region,
  city: s.kommune || s.region,
  county: s.kommune,
  addressDetail: [s.address, s.addressTwo].filter(Boolean).join(', '),
  postalCode: s.zip,
  isDefault: false,
});

/**
 * Customer checkout — a SINGLE order-confirm screen modelled on litemall-vue's
 * `order/checkout`: an address cell, a coupon cell, the goods line-cards, a money
 * summary, an order note, the payment-method choice, and a sticky bottom submit
 * bar — rather than a multi-step wizard. Placement is the two-step place→pay flow
 * (`POST /srv/order/submit` then `/srv/order/{id}/actions/pay`); see orderSlice.
 */
const CheckoutView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const { loading: orderLoading, errorMessage: orderError, phase } = useAppSelector(state => state.order);

  const [shipping, setShipping] = useState<ShippingInfo>(EMPTY_SHIPPING);
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');
  const [message, setMessage] = useState('');
  // Order id once placed — retained so a payment retry pays the SAME order.
  const [placedOrderId, setPlacedOrderId] = useState<number | null>(null);
  const [addrError, setAddrError] = useState<string | null>(null);

  // Address book (graceful when /srv/address isn't reachable).
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
  const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const { name, value } = e.target;
    setShipping(prev => ({ ...prev, [name]: value }));
  };

  const pickAddress = (a: IAddress) => {
    setSelectedAddressId(a.id ?? 'new');
    setShipping(addressToShipping(a));
  };

  const usingNewAddress = selectedAddressId === 'new' || addresses.length === 0;
  // Saved address is pre-validated; a new address needs the core fields.
  const addressValid = usingNewAddress ? !!(shipping.name && shipping.address && shipping.region && shipping.zip) : selectedAddressId != null;
  const savedAddressId = typeof selectedAddressId === 'number' ? selectedAddressId : null;

  const couponCellValue =
    couponDiscount > 0 ? `−$${couponDiscount.toFixed(2)}` : coupons.length > 0 ? `${coupons.length} available` : 'None available';

  // Place (once) then pay, so a payment retry never creates a second order.
  const handlePlaceOrder = async () => {
    setAddrError(null);

    // 1. Resolve a saved addressId, persisting the typed address if needed.
    let addressId = savedAddressId;
    if (addressId == null) {
      if (!addressValid) return; // guarded by the disabled button below
      try {
        const newId = await userApi.addressSave(shippingToAddress(shipping));
        addressId = typeof newId === 'number' ? newId : Number(newId);
        if (!addressId) throw new Error('no id');
        setSelectedAddressId(addressId);
      } catch (e) {
        setAddrError(
          isMissingEndpoint(e)
            ? 'The address book service is unavailable right now. Please try again later.'
            : 'Could not save the delivery address. Check the required fields and try again.'
        );
        return;
      }
    }

    // 2. Place the order (only if not already placed).
    let orderId = placedOrderId;
    if (orderId == null) {
      const placed = await dispatch(placeOrder({ cartId: 0, addressId, userCouponId: selectedCouponId ?? undefined, message }));
      if (!placeOrder.fulfilled.match(placed)) return; // stock/validation error shown from order state
      orderId = placed.payload.orderId;
      setPlacedOrderId(orderId);
    }

    // 3. Pay the placed order (WALLET debit / CARD stub).
    const paid = await dispatch(payOrder({ orderId, paymentMethod }));
    if (payOrder.fulfilled.match(paid)) {
      dispatch(clearCart());
      navigate(`/order-confirmation/${orderId}`);
    }
    // On payment failure the error is shown from order state; placedOrderId is
    // retained so the submit bar retries payment on the same order.
  };

  // Empty-cart guard.
  if (cartList.length === 0) {
    return (
      <Page>
        <PageHead title='Checkout' />
        <div className='container'>
          <CellGroup>
            <EmptyState icon='bi-cart-x' text='Your cart is empty.'>
              <Link to='/' className='btn btn-lm-primary'>
                Continue shopping
              </Link>
            </EmptyState>
          </CellGroup>
        </div>
      </Page>
    );
  }

  const submitting = orderLoading === 'pending';

  return (
    <Page>
      <PageHead title='Checkout' />
      <div className='container'>
        {/* Delivery address */}
        <CellGroup title='Delivery address'>
          {addresses.length > 0 && (
            <div className='p-2 d-grid gap-2'>
              {addresses.map(a => (
                <AddressCard
                  key={a.id}
                  name={a.name}
                  tel={a.tel}
                  detail={[a.province, a.city, a.county, a.addressDetail].filter(Boolean).join(' ')}
                  isDefault={a.isDefault}
                  active={selectedAddressId === a.id}
                  onClick={() => pickAddress(a)}
                />
              ))}
              <button
                type='button'
                className={`lm-address-card lm-address-card--new ${selectedAddressId === 'new' ? 'is-active' : ''}`}
                onClick={() => {
                  setSelectedAddressId('new');
                  setShipping(EMPTY_SHIPPING);
                }}
              >
                <i className='bi bi-plus-lg me-1' /> Use a new address
              </button>
            </div>
          )}

          {usingNewAddress && (
            <div className='row g-3 p-3'>
              <div className='col-md-6'>
                <Form.Label>Full name *</Form.Label>
                <Form.Control name='name' value={shipping.name} onChange={handleInputChange} required />
              </div>
              <div className='col-md-6'>
                <Form.Label>Mobile</Form.Label>
                <Form.Control name='mobile' value={shipping.mobile} onChange={handleInputChange} />
              </div>
              <div className='col-12'>
                <Form.Label>Email</Form.Label>
                <Form.Control name='email' type='email' value={shipping.email} onChange={handleInputChange} />
              </div>
              <div className='col-12'>
                <Form.Label>Address line 1 *</Form.Label>
                <Form.Control name='address' value={shipping.address} onChange={handleInputChange} required />
              </div>
              <div className='col-12'>
                <Form.Label>Address line 2</Form.Label>
                <Form.Control name='addressTwo' value={shipping.addressTwo} onChange={handleInputChange} />
              </div>
              <div className='col-md-4'>
                <Form.Label>Region *</Form.Label>
                <Form.Select name='region' value={shipping.region} onChange={handleSelectChange} required>
                  <option value=''>-- Region --</option>
                  {REGIONS.map(r => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </Form.Select>
              </div>
              <div className='col-md-4'>
                <Form.Label>Kommune</Form.Label>
                <Form.Select name='kommune' value={shipping.kommune} onChange={handleSelectChange}>
                  <option value=''>-- Kommune --</option>
                  {REGIONS.map(r => (
                    <option key={r} value={r}>
                      {r}
                    </option>
                  ))}
                </Form.Select>
              </div>
              <div className='col-md-4'>
                <Form.Label>Zip *</Form.Label>
                <Form.Control name='zip' value={shipping.zip} onChange={handleInputChange} required />
              </div>
            </div>
          )}
        </CellGroup>

        {/* Coupon */}
        {coupons.length > 0 && (
          <CellGroup>
            <Cell title='Coupon'>
              <Form.Select
                size='sm'
                value={selectedCouponId ?? ''}
                onChange={e => setSelectedCouponId(e.target.value ? Number(e.target.value) : null)}
              >
                <option value=''>No coupon ({couponCellValue})</option>
                {coupons.map(c => (
                  <option key={c.id} value={c.id} disabled={(c.min ?? 0) > cartTotalAmount}>
                    −${c.discount} {c.min ? `(over $${c.min})` : ''}
                  </option>
                ))}
              </Form.Select>
            </Cell>
          </CellGroup>
        )}

        {/* Goods */}
        <CellGroup title={`Items (${cartList.length})`}>
          {cartList.map(item => (
            <GoodsLineCard
              key={item.id}
              picUrl={item.picUrl}
              name={item.goodsName}
              to={item.goodsId ? `/product/${item.goodsId}` : undefined}
              specs={item.specifications}
              price={(item.price ?? 0) * (item.number ?? 0)}
              qty={item.number ?? 0}
            />
          ))}
        </CellGroup>

        {/* Order note */}
        <CellGroup>
          <div className='p-3'>
            <Form.Label className='small text-muted mb-1'>Order note</Form.Label>
            <Form.Control
              as='textarea'
              rows={2}
              maxLength={50}
              placeholder='Leave a note for this order (optional)'
              value={message}
              onChange={e => setMessage(e.target.value)}
            />
            <div className='text-end small text-muted'>{message.length}/50</div>
          </div>
        </CellGroup>

        {/* Summary */}
        <CellGroup>
          <OrderSummary
            rows={[
              { label: 'Goods total', value: `$${cartTotalAmount.toFixed(2)}` },
              { label: 'Shipping', value: 'Free', variant: 'muted' },
              ...(couponDiscount > 0 ? [{ label: 'Coupon', value: `−$${couponDiscount.toFixed(2)}`, variant: 'success' as const }] : []),
              { label: 'Total', value: `$${grandTotal.toFixed(2)}`, variant: 'total' },
            ]}
          />
        </CellGroup>

        {/* Payment method */}
        <CellGroup title='Payment method'>
          <Cell>
            <Form.Check
              type='radio'
              id='pay-card'
              name='paymentMethod'
              label='Credit / debit card'
              checked={paymentMethod === 'CARD'}
              onChange={() => setPaymentMethod('CARD')}
            />
          </Cell>
          <Cell>
            <Form.Check
              type='radio'
              id='pay-wallet'
              name='paymentMethod'
              label='Digital wallet (balance)'
              checked={paymentMethod === 'WALLET'}
              onChange={() => setPaymentMethod('WALLET')}
            />
          </Cell>
          <div className='px-3 pb-3'>
            {paymentMethod === 'CARD' && (
              <Alert variant='light' className='border mb-0'>
                Card payment runs when you place the order. Real Stripe card capture is a pending integration, so a placeholder
                authorisation is used for now.
              </Alert>
            )}
            {paymentMethod === 'WALLET' && (
              <Alert variant='light' className='border mb-0'>
                Your wallet balance is debited when the order is placed. An insufficient balance leaves the order unpaid and shows an
                error — nothing is charged.
              </Alert>
            )}
          </div>
        </CellGroup>

        {/* Errors */}
        {placedOrderId != null && (orderError || addrError) && (
          <Alert variant='info'>
            Your order <strong>#{placedOrderId}</strong> was placed but payment did not complete. Use “Retry payment” to charge it again.
          </Alert>
        )}
        {addrError && <Alert variant='danger'>{addrError}</Alert>}
        {orderError && <Alert variant='danger'>{orderError}</Alert>}
      </div>

      <SubmitBar
        total={grandTotal}
        buttonText={
          submitting
            ? phase === 'paying'
              ? 'Processing payment…'
              : 'Placing order…'
            : placedOrderId != null
              ? 'Retry payment'
              : 'Place order'
        }
        onSubmit={handlePlaceOrder}
        disabled={!addressValid}
        loading={submitting}
      />
    </Page>
  );
};

export default CheckoutView;
