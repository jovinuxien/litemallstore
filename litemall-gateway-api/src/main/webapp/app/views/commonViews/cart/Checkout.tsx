import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Form } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import { CheckoutPaymentMethod, OrderGroup, PlacedOrder, payOrder, placeOrder, resetOrderState, ShippingInfo } from 'app/shared/reducers/orderSlice';
import { IAddress, ICoupon, isMissingEndpoint, orderApi, userApi } from 'app/shared/api';
import { IFreightQuote } from 'app/shared/model/order/order.model';
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
  it.source === 'cj' || it.source === 'cj_dropshipping' || String(it.goodsId ?? '').startsWith('cj_');

const EMPTY_SHIPPING: ShippingInfo = {
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
  country: '',
  countryCode: '',
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
 * bar. Local and CJ Dropshipping lines both run the two-step place->pay flow
 * (`POST /srv/order/submit` then `/srv/order/{id}/actions/pay`), one order per cart
 * group — the order service rejects mixed carts. CJ lines additionally need a
 * destination country + phone; paying a CJ order also places it at CJ server-side.
 */
const CheckoutView: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const { loading: orderLoading, errorMessage: orderError, phase } = useAppSelector(state => state.order);

  const [shipping, setShipping] = useState<ShippingInfo>(EMPTY_SHIPPING);
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');
  const [message, setMessage] = useState('');
  // CJ destination country, kept separate so picking a saved address doesn't clear it.
  const [country, setCountry] = useState<{ name: string; code: string }>({ name: '', code: '' });
  // Orders created by submit, keyed by cart group — retained so a payment retry
  // pays the SAME order(s) and never re-submits an already-placed group.
  const [placed, setPlaced] = useState<{ local?: PlacedOrder; cj?: PlacedOrder }>({});
  const [addrError, setAddrError] = useState<string | null>(null);

  // Address book (graceful when /srv/address isn't reachable).
  const [addresses, setAddresses] = useState<IAddress[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | 'new' | null>(null);

  // Coupons (graceful when /srv/coupon isn't live yet).
  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);

  // CJ lines ship via CJ Dropshipping, which requires a country + phone.
  const hasCjItems = useMemo(() => cartList.some(isCjItem), [cartList]);

  // Freight/logistics quote per cart group (submit creates one order per group, each
  // charged its own freight). The CJ quote additionally carries the informational
  // carrier + delivery estimate once a destination country is picked.
  const [quotes, setQuotes] = useState<{ local?: IFreightQuote | null; cj?: IFreightQuote | null }>({});
  const [quoteLoading, setQuoteLoading] = useState(false);
  const cartSignature = useMemo(
    () => cartList.map(it => `${it.goodsId}:${it.productId ?? ''}:${it.number ?? 0}:${it.price ?? 0}`).join('|'),
    [cartList]
  );

  useEffect(() => {
    if (cartList.length === 0) {
      setQuotes({});
      return undefined;
    }
    let cancelled = false;
    setQuoteLoading(true);
    // Debounced: qty steppers / country switches re-render often, and the CJ quote is a
    // (server-cached) upstream call.
    const timer = setTimeout(async () => {
      const localItems = cartList.filter(it => !isCjItem(it));
      const cjItems = cartList.filter(isCjItem);
      const subtotalOf = (items: typeof cartList) => items.reduce((s, it) => s + (it.price ?? 0) * (it.number ?? 0), 0);
      const next: { local?: IFreightQuote | null; cj?: IFreightQuote | null } = {};
      if (localItems.length > 0) {
        next.local = await orderApi.freightQuote({ subtotal: subtotalOf(localItems) }).catch(() => null);
      }
      if (cjItems.length > 0) {
        next.cj = await orderApi
          .freightQuote({
            countryCode: country.code || undefined,
            subtotal: subtotalOf(cjItems),
            cjItems: cjItems.filter(it => it.productId != null).map(it => ({ productId: it.productId, quantity: it.number ?? 1 })),
          })
          .catch(() => null);
      }
      if (!cancelled) {
        setQuotes(next);
        setQuoteLoading(false);
      }
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cartSignature, country.code]);

  // Quote failure / endpoint missing → fee 0 (today's behavior); the server still charges
  // its rule at submit, so this is display-best-effort, never a checkout blocker.
  const shippingFee = (quotes.local?.freightPrice ?? 0) + (quotes.cj?.freightPrice ?? 0);

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
  const grandTotal = Math.max(0, cartTotalAmount - couponDiscount + shippingFee);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    setShipping(prev => ({ ...prev, [name]: value }));
  };
  const handleSelectChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const { name, value } = e.target;
    setShipping(prev => ({ ...prev, [name]: value }));
  };
  const handleCountryChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const code = e.target.value;
    setCountry({ code, name: COUNTRIES.find(c => c.code === code)?.name ?? '' });
  };

  const pickAddress = (a: IAddress) => {
    setSelectedAddressId(a.id ?? 'new');
    setShipping(addressToShipping(a));
  };

  const usingNewAddress = selectedAddressId === 'new' || addresses.length === 0;
  // Saved address is pre-validated; a new address needs the core fields. CJ orders
  // additionally need a phone and a destination country.
  const baseValid = usingNewAddress ? !!(shipping.name && shipping.address && shipping.region && shipping.zip) : selectedAddressId != null;
  const addressValid = baseValid && (!hasCjItems || !!(shipping.mobile && country.code));
  const savedAddressId = typeof selectedAddressId === 'number' ? selectedAddressId : null;

  const couponCellValue =
    couponDiscount > 0 ? `−$${couponDiscount.toFixed(2)}` : coupons.length > 0 ? `${coupons.length} available` : 'None available';

  // Place each cart group (once) then pay, so a retry never creates a second order.
  const handlePlaceOrder = async () => {
    setAddrError(null);

    // 1. Resolve a saved addressId for the local order, persisting a typed address.
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

    // 2. Submit one order per cart group (local first — the order service rejects
    //    carts mixing CJ and local goods). Groups run SEQUENTIALLY: they share the
    //    one server cart and each mirror starts by wiping it. A group that already
    //    has an order (payment retry) is skipped, so a retry never re-submits.
    const groups: Array<{ group: OrderGroup; items: typeof cartList }> = [
      { group: 'local' as const, items: cartList.filter(it => !isCjItem(it)) },
      { group: 'cj' as const, items: cartList.filter(isCjItem) },
    ].filter(g => g.items.length > 0);

    const next = { ...placed };
    for (const { group, items } of groups) {
      if (next[group]) continue;
      // eslint-disable-next-line no-await-in-loop
      const res = await dispatch(
        placeOrder({
          group,
          items,
          addressId,
          message,
          paymentMethod,
          // A coupon redeems once — it rides the first submitted order only.
          userCouponId: group === groups[0].group ? selectedCouponId ?? undefined : undefined,
          // CJ placement (at pay time) needs the destination country.
          countryCode: group === 'cj' ? country.code : undefined,
        })
      );
      if (!placeOrder.fulfilled.match(res)) {
        setPlaced(next); // keep what was placed so a retry skips those groups
        return; // stock/validation error shown from order state
      }
      next[group] = res.payload;
      setPlaced({ ...next });
    }

    // 3. Pay each placed, still-unpaid order (WALLET debit / CARD stub). Paying a CJ
    //    order also places it at CJ — orderSlice stretches that request's timeout.
    for (const group of ['local', 'cj'] as const) {
      const ord = next[group];
      if (!ord || ord.paid) continue;
      // eslint-disable-next-line no-await-in-loop
      const res = await dispatch(payOrder({ orderId: ord.orderId, paymentMethod, group }));
      if (!payOrder.fulfilled.match(res)) return; // error shown from order state; retry pays only unpaid orders
      next[group] = { ...ord, paid: true };
      setPlaced({ ...next });
    }

    // 4. Everything paid — clear the cart and confirm (local order id leads).
    dispatch(clearCart());
    navigate(`/order-confirmation/${(next.local ?? next.cj)!.orderId}`);
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
  const placedList = [placed.local, placed.cj].filter((o): o is PlacedOrder => !!o);
  const anyPlaced = placedList.length > 0;
  const paidOrder = placedList.find(o => o.paid);
  const unpaidOrders = placedList.filter(o => !o.paid);

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
                <Form.Label>Mobile{hasCjItems ? ' *' : ''}</Form.Label>
                <Form.Control name='mobile' value={shipping.mobile} onChange={handleInputChange} required={hasCjItems} />
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

          {/* CJ Dropshipping needs a destination country + phone regardless of which
              address is used. */}
          {hasCjItems && (
            <div className='px-3 pb-3'>
              <Alert variant='info' className='mb-2'>
                Some items ship via <strong>CJ Dropshipping</strong> — please provide a <strong>country</strong> and a <strong>phone number</strong>.
              </Alert>
              <Form.Label>Destination country *</Form.Label>
              <Form.Select value={country.code} onChange={handleCountryChange} required>
                <option value=''>-- Country --</option>
                {COUNTRIES.map(c => (
                  <option key={c.code} value={c.code}>
                    {c.name}
                  </option>
                ))}
              </Form.Select>
              {/* Informational logistics line for the CJ group (carrier + delivery estimate). */}
              {country.code && (
                <div className='mt-2 small'>
                  {quoteLoading ? (
                    <span className='text-muted'>Checking logistics…</span>
                  ) : quotes.cj?.cj?.logisticName ? (
                    <span>
                      <i className='bi bi-truck me-1' />
                      Ships via <strong>{quotes.cj.cj.logisticName}</strong>
                      {quotes.cj.cj.logisticAging ? <> · estimated delivery {quotes.cj.cj.logisticAging} days</> : null}
                    </span>
                  ) : quotes.cj?.cjNote ? (
                    <span className='text-muted'>{quotes.cj.cjNote}</span>
                  ) : null}
                </div>
              )}
              {!usingNewAddress && (
                <div className='mt-2'>
                  <Form.Label>Phone *</Form.Label>
                  <Form.Control name='mobile' value={shipping.mobile} onChange={handleInputChange} required />
                </div>
              )}
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
              {
                label: 'Shipping',
                value: quoteLoading ? '…' : shippingFee > 0 ? `$${shippingFee.toFixed(2)}` : 'Free',
                variant: 'muted' as const,
              },
              ...(shippingFee > 0 && (quotes.local?.freeShippingThreshold ?? quotes.cj?.freeShippingThreshold ?? 0) > 0
                ? [
                    {
                      label: 'Free shipping',
                      value: `on orders over $${Number(
                        quotes.local?.freeShippingThreshold ?? quotes.cj?.freeShippingThreshold
                      ).toFixed(2)}`,
                      variant: 'muted' as const,
                    },
                  ]
                : []),
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
        {anyPlaced && (orderError || addrError) && (
          <Alert variant='info'>
            {paidOrder && unpaidOrders.length > 0 ? (
              <>
                Order <strong>#{paidOrder.orderId}</strong> is paid, but payment for <strong>#{unpaidOrders[0].orderId}</strong> did not
                complete. “Retry payment” charges only the unpaid order.
              </>
            ) : (
              <>Your order was placed but payment did not complete. “Retry payment” will not create a new order.</>
            )}
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
              ? hasCjItems
                ? 'Processing payment… (dropship orders can take up to 30 seconds)'
                : 'Processing payment…'
              : 'Placing order…'
            : anyPlaced
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
