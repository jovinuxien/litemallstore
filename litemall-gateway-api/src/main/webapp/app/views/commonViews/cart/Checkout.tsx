import { Elements } from '@stripe/react-stripe-js';
import type { Stripe } from '@stripe/stripe-js';
// '/pure', NOT the main entry: importing '@stripe/stripe-js' directly INJECTS js.stripe.com
// as an import side effect, so merely bundling it would hit Stripe (and its
// m.stripe.network fingerprinting endpoints) on page load — with no publishable key, no
// card selected, and no consent. '/pure' defers all of that until loadStripe() is called.
// Verified: with this import the unconfigured checkout makes ZERO requests to stripe.com.
import { loadStripe } from '@stripe/stripe-js/pure';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Form } from 'react-bootstrap';
import { Link, useNavigate } from 'react-router-dom';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loadSiteConfig } from 'app/shared/config/siteConfig';
import StripeCardForm, { StripeCardHandle } from 'app/shared/payment/StripeCardForm';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import {
  CheckoutPaymentMethod,
  CheckoutTotals,
  OrderGroup,
  PlacedOrder,
  payOrder,
  placeOrder,
  previewCheckoutTotals,
  resetOrderState,
  ShippingInfo,
  TaxUnavailableError,
} from 'app/shared/reducers/orderSlice';
import { IAddress, ICoupon, orderApi, userApi } from 'app/shared/api';
import { IFreightQuote, IStore } from 'app/shared/model/order/order.model';
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

/**
 * Stripe.js is loaded once per page, lazily, and ONLY when a publishable key exists —
 * an unconfigured deployment must make no Stripe request at all, the same discipline the
 * Matomo tracker follows. Cached because loadStripe() must not run per render.
 */
let stripeJs: Promise<Stripe | null> | null = null;
const stripePromiseFor = (publishableKey: string): Promise<Stripe | null> => {
  stripeJs = stripeJs ?? loadStripe(publishableKey);
  return stripeJs;
};

/**
 * Must match `litemall.order.stripe.currency` (order's default). A mismatch is safe rather
 * than silent: the server asserts the PaymentIntent's currency at pay time and rejects.
 */
const STRIPE_CURRENCY = 'usd';

/**
 * Render a server-computed amount. `undefined` shows a placeholder rather than $0.00 —
 * "we haven't been told yet" and "it's free" must never look the same on a checkout.
 */
const money = (v?: number): string => (v == null ? '—' : `$${v.toFixed(2)}`);

/** Pull a readable message out of an axios/envelope error. */
const messageOf = (error: unknown, fallback: string): string => {
  const data = (error as { response?: { data?: { message?: string; errmsg?: string } } })?.response?.data;
  return data?.message ?? data?.errmsg ?? (error as { errmsg?: string })?.errmsg ?? fallback;
};

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

  // Server-computed money (order Wave-7 §4). The client no longer totals anything: with
  // tax it provably cannot, and without tax it merely disagreed silently.
  const [totals, setTotals] = useState<CheckoutTotals | null>(null);
  const [totalsLoading, setTotalsLoading] = useState(false);
  // Set when the server refuses to total the cart (tax fails CLOSED). Blocks checkout —
  // never fall back to a client-side sum the server has said it will not charge.
  const [totalsBlocked, setTotalsBlocked] = useState<string | null>(null);

  // Stripe publishable key from /auth/site-config. null ⇒ card payment is cleanly
  // unavailable (never stubbed) and the UI falls back to wallet.
  const [publishableKey, setPublishableKey] = useState<string | null>(null);
  const [siteConfigLoaded, setSiteConfigLoaded] = useState(false);
  const [cardError, setCardError] = useState<string | null>(null);
  const cardRef = useRef<StripeCardHandle>(null);

  // Address book (graceful when /srv/address isn't reachable).
  const [addresses, setAddresses] = useState<IAddress[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | 'new' | null>(null);

  // Coupons usable for THIS checkout, from /srv/coupon/selectlist (promotion
  // service; graceful empty while it isn't live). Item id = userCouponId (the
  // redeem handle), cid = the coupon definition id — submit sends both.
  const [coupons, setCoupons] = useState<ICoupon[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);
  // Server-side coupon rejection at submit (e.g. redeemed elsewhere meanwhile),
  // surfaced inline at the picker rather than only as the page-level alert.
  const [couponError, setCouponError] = useState<string | null>(null);

  // CJ lines ship via CJ Dropshipping, which requires a country + phone.
  const hasCjItems = useMemo(() => cartList.some(isCjItem), [cartList]);

  // Pickup checkout (Wave 4 — handoff-gateway-api-pickup.md; the toggle only
  // appears when /srv/store/list has visible stores). CJ lines always ship —
  // pickup is offered for all-local carts only.
  const [stores, setStores] = useState<IStore[]>([]);
  const [deliveryType, setDeliveryType] = useState<'express' | 'pickup'>('express');
  const [selectedStoreId, setSelectedStoreId] = useState<number | null>(null);
  const [pickupName, setPickupName] = useState('');
  const [pickupMobile, setPickupMobile] = useState('');
  const isPickup = deliveryType === 'pickup' && !hasCjItems;

  useEffect(() => {
    orderApi
      .storeList()
      .then(list => setStores(list ?? []))
      .catch(() => setStores([])); // endpoint absent → toggle stays hidden
  }, []);

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
      // Pickup charges no freight — skip the local quote entirely.
      if (localItems.length > 0 && !isPickup) {
        next.local = await orderApi
          .freightQuote({
            subtotal: subtotalOf(localItems),
            // Wave 4 freight templates: the selected address resolves the region
            // rule; cart lines drive per-template pricing.
            addressId: typeof selectedAddressId === 'number' ? selectedAddressId : undefined,
            items: localItems.map(it => ({ goodsId: it.goodsId, quantity: it.number ?? 1, price: it.price ?? 0 })),
          })
          .catch(() => null);
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
    // selectedAddressId in the deps → re-quote on address change (region-rule
    // freight varies by destination province); isPickup → drop/restore the
    // local quote when the delivery method flips.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cartSignature, country.code, selectedAddressId, isPickup]);

  // Quote failure / endpoint missing → fee 0 (today's behavior); the server still charges
  // its rule at submit, so this is display-best-effort, never a checkout blocker.
  // Pickup: no shipping — freight is 0 by contract.
  // NOTE: the quotes now drive only the per-template BREAKDOWN detail rows. The charged
  // freight comes from `totals.freightPrice` below, which the server computed with this
  // same freight service — one number, one source.
  const shippingFee = isPickup ? 0 : (quotes.local?.freightPrice ?? 0) + (quotes.cj?.freightPrice ?? 0);

  /**
   * Server-authoritative totals. Mirrors each cart group into the server cart and prices
   * it — the same sequence submit runs, so preview and charge agree by construction.
   *
   * Keyed on everything the server prices against: the lines, the destination (address +
   * country, which is what makes tax resolvable), and the coupon. Debounced like the
   * freight quote — qty steppers re-render often and this writes the server cart.
   */
  useEffect(() => {
    if (cartList.length === 0) {
      setTotals(null);
      setTotalsBlocked(null);
      return undefined;
    }
    let cancelled = false;
    setTotalsLoading(true);
    const timer = setTimeout(async () => {
      try {
        // Local first, then CJ — the same order handlePlaceOrder submits in, so the
        // coupon lands on the same group in the preview as it will on the charge.
        const groups = [cartList.filter(it => !isCjItem(it)), cartList.filter(isCjItem)];
        const next = await previewCheckoutTotals(groups, {
          addressId: typeof selectedAddressId === 'number' ? selectedAddressId : undefined,
          couponId: selectedCouponId ?? undefined,
          countryCode: country.code || undefined,
        });
        if (cancelled) return;
        setTotals(next);
        setTotalsBlocked(null);
      } catch (error) {
        if (cancelled) return;
        setTotals(null);
        setTotalsBlocked(
          error instanceof TaxUnavailableError
            ? error.message
            : "We can't total your cart right now — please try again.",
        );
      } finally {
        if (!cancelled) setTotalsLoading(false);
      }
    }, 500);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cartSignature, country.code, selectedAddressId, selectedCouponId, isPickup]);

  useEffect(() => {
    loadSiteConfig().then(cfg => {
      setPublishableKey(cfg.stripePublishableKey);
      setSiteConfigLoaded(true);
    });
  }, []);

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
      .catch(() => setSelectedAddressId('new'));
  }, [dispatch]);

  // Usable-coupon query: the caller supplies the cart facts (promotion has no
  // cart access) — subtotal + the numeric goods ids. Re-runs when the cart
  // changes so threshold coupons appear/disappear with the total.
  useEffect(() => {
    if (cartList.length === 0) {
      setCoupons([]);
      return;
    }
    const amount = cartList.reduce((sum, it) => sum + (it.price ?? 0) * (it.number ?? 0), 0);
    const goodsIds = cartList.map(it => Number(it.goodsId)).filter(id => Number.isFinite(id));
    userApi
      .couponSelectList(Number(amount.toFixed(2)), goodsIds)
      .then(list => setCoupons(list ?? []))
      .catch(() => setCoupons([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cartSignature]);

  // A refreshed usable-list can drop the selected coupon (threshold no longer met).
  useEffect(() => {
    if (selectedCouponId != null && !coupons.some(c => c.id === selectedCouponId)) {
      setSelectedCouponId(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [coupons]);

  /**
   * Display-only, and NOT money we charge: this drives the coupon picker's "spend $X more"
   * hint and the CJ/local group split. The charged figures all come from `totals`.
   */
  const cartLinesSubtotal = useMemo(
    () => cartList.reduce((sum, item) => sum + (item.price ?? 0) * (item.number ?? 0), 0),
    [cartList]
  );

  const selectedCoupon = useMemo(() => coupons.find(c => c.id === selectedCouponId) ?? null, [coupons, selectedCouponId]);

  /** Card payment needs a publishable key. Absent ⇒ unavailable, never stubbed. */
  const cardAvailable = !!publishableKey;

  /**
   * Deferred-intent Elements: the amount is declared up front so the card form can mount
   * before an order exists, and the intent is created only once the order does. Amount is
   * in minor units and comes from the SERVER total — the browser never decides it.
   *
   * Null while the total is unknown: mounting Elements with a guessed amount would show
   * the customer a figure we might not charge.
   */
  const stripeElementsOptions = useMemo(
    () =>
      totals && totals.actualPrice > 0
        ? { mode: 'payment' as const, amount: Math.round(totals.actualPrice * 100), currency: STRIPE_CURRENCY }
        : null,
    [totals],
  );

  // A deployment without Stripe must not strand the customer on a dead radio.
  useEffect(() => {
    if (siteConfigLoaded && !cardAvailable && paymentMethod === 'CARD') {
      setPaymentMethod('WALLET');
    }
  }, [siteConfigLoaded, cardAvailable, paymentMethod]);

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
  // Pickup needs a store + contact instead of a delivery address.
  const pickupValid = selectedStoreId != null && !!pickupName && !!pickupMobile;
  const checkoutValid = isPickup ? pickupValid : addressValid;

  const couponCellValue =
    (totals?.couponPrice ?? 0) > 0
      ? `−$${totals!.couponPrice.toFixed(2)}`
      : coupons.length > 0
        ? `${coupons.length} available`
        : 'None available';

  // Place each cart group (once) then pay, so a retry never creates a second order.
  const handlePlaceOrder = async () => {
    setAddrError(null);
    setCouponError(null);
    setCardError(null);

    // 0. Tax fails CLOSED: without a server total there is no number we are allowed to
    //    charge, so the order is not placed at all.
    if (totalsBlocked || !totals) {
      setCardError(totalsBlocked ?? "We can't total your cart right now — please try again.");
      return;
    }

    // 0b. Validate the card BEFORE creating an order. Stripe requires elements.submit()
    //     ahead of confirmation, and a customer with a typo'd card should not be left with
    //     a placed-but-unpaid order.
    if (paymentMethod === 'CARD' && !anyPlaced) {
      const ok = await cardRef.current?.validate();
      if (!ok) return; // reason rendered inline by StripeCardForm
    }

    // 1. Resolve a saved addressId for the local order, persisting a typed address.
    //    Pickup orders need no delivery address — the store is the destination.
    let addressId = savedAddressId;
    if (addressId == null && !isPickup) {
      if (!addressValid) return; // guarded by the disabled button below
      try {
        const newId = await userApi.addressSave(shippingToAddress(shipping));
        addressId = typeof newId === 'number' ? newId : Number(newId);
        if (!addressId) throw new Error('no id');
        setSelectedAddressId(addressId);
      } catch {
        setAddrError('Could not save the delivery address. Check the required fields and try again.');
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
      // A coupon redeems once — it rides the first submitted order only.
      const couponRides = group === groups[0].group && selectedCoupon != null;
      const res = await dispatch(
        placeOrder({
          group,
          items,
          addressId: isPickup && group === 'local' ? undefined : addressId ?? undefined,
          message,
          paymentMethod,
          // Pickup rides the local group only (CJ always ships).
          deliveryType: isPickup && group === 'local' ? 'pickup' : undefined,
          storeId: isPickup && group === 'local' ? selectedStoreId ?? undefined : undefined,
          pickupName: isPickup && group === 'local' ? pickupName : undefined,
          pickupMobile: isPickup && group === 'local' ? pickupMobile : undefined,
          // id = userCouponId (redeem handle), cid = coupon definition id.
          couponId: couponRides ? selectedCoupon.cid : undefined,
          userCouponId: couponRides ? selectedCoupon.id : undefined,
          // CJ placement (at pay time) needs the destination country.
          countryCode: group === 'cj' ? country.code : undefined,
        })
      );
      if (!placeOrder.fulfilled.match(res)) {
        // A rejected submit that carried a coupon gets the inline
        // remove-and-retry affordance at the picker on top of the page alert:
        // the coupon is the one submit input the customer can drop and retry.
        // Order rejects promotion-issued coupons as a generic "System internal
        // error" until its promotion facade lands, so matching /coupon/i in
        // the message alone misses the common case; only the slice's own
        // pre-submit rejections (401 sign-in, 400 stale items) are excluded.
        const { errno = 0, errmsg = '' } = (res.payload as { errno?: number; errmsg?: string } | undefined) ?? {};
        if (couponRides && errno !== 400 && errno !== 401) {
          setCouponError(/coupon/i.test(errmsg) ? errmsg : `The selected coupon may not be usable for this order${errmsg ? ` (${errmsg})` : ''}.`);
        }
        setPlaced(next); // keep what was placed so a retry skips those groups
        return; // stock/validation error shown from order state
      }
      next[group] = res.payload;
      setPlaced({ ...next });
    }

    // 3. Pay each placed, still-unpaid order. WALLET debits server-side. CARD creates a
    //    PaymentIntent for THIS order (server-side, from the server's own total), confirms
    //    it with Elements, and reports the real id — the server then re-retrieves it and
    //    asserts status/amount/currency/metadata.orderId before marking the order paid.
    //    Paying a CJ order also places it at CJ — orderSlice stretches that timeout.
    //
    //    The intent is created per order, after that order exists: a charge must never
    //    exist without an order behind it. A mixed cart therefore confirms the card once
    //    per group, which is why each group's amount is its own intent.
    for (const group of ['local', 'cj'] as const) {
      const ord = next[group];
      if (!ord || ord.paid) continue;

      let paymentIntentId: string | undefined;
      if (paymentMethod === 'CARD') {
        let intent: { clientSecret: string };
        try {
          // eslint-disable-next-line no-await-in-loop
          intent = await orderApi.paymentIntent(ord.orderId);
        } catch (error) {
          // Stripe disabled or failing ⇒ a typed error. The order stays placed and
          // unpaid, and the customer is told — we do not invent a payment.
          setCardError(
            messageOf(error, 'Card payment is unavailable right now. Your order is saved but not paid.'),
          );
          setPlaced(next);
          return;
        }
        // eslint-disable-next-line no-await-in-loop
        const confirmedId = await cardRef.current?.confirm(intent.clientSecret);
        // null ⇒ StripeCardForm has rendered the reason inline; the order stays unpaid
        // and the customer can retry, which pays this same order rather than re-placing.
        if (!confirmedId) {
          setPlaced(next);
          return;
        }
        paymentIntentId = confirmedId;
      }

      // eslint-disable-next-line no-await-in-loop
      const res = await dispatch(payOrder({ orderId: ord.orderId, paymentMethod, paymentIntentId, group }));
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
        {/* Delivery method (Wave 4 pickup — only offered when the order service
            has stores AND the cart is all-local; CJ lines always ship). */}
        {stores.length > 0 && !hasCjItems && (
          <CellGroup title='Delivery method'>
            <div className='p-3 d-flex gap-4'>
              <Form.Check
                type='radio'
                id='delivery-express'
                name='deliveryType'
                label='Ship to me'
                checked={deliveryType === 'express'}
                onChange={() => setDeliveryType('express')}
              />
              <Form.Check
                type='radio'
                id='delivery-pickup'
                name='deliveryType'
                label='Store pickup (free)'
                checked={deliveryType === 'pickup'}
                onChange={() => setDeliveryType('pickup')}
              />
            </div>
          </CellGroup>
        )}

        {/* Pickup: store picker + pickup contact replace the address book. */}
        {isPickup && (
          <CellGroup title='Pickup store'>
            <div className='p-2 d-grid gap-2'>
              {stores.map(s => (
                <button
                  key={s.id}
                  type='button'
                  className={`lm-address-card text-start ${selectedStoreId === s.id ? 'is-active' : ''}`}
                  onClick={() => setSelectedStoreId(s.id ?? null)}
                >
                  <div className='fw-semibold'>{s.name}</div>
                  <div className='small text-muted'>{[s.address, s.detailedAddress].filter(Boolean).join(' ')}</div>
                  <div className='small text-muted'>
                    {s.businessHours && (
                      <span className='me-3'>
                        <i className='bi bi-clock me-1' />
                        {s.businessHours}
                      </span>
                    )}
                    {s.phone && (
                      <span>
                        <i className='bi bi-telephone me-1' />
                        {s.phone}
                      </span>
                    )}
                  </div>
                </button>
              ))}
            </div>
            <div className='row g-3 p-3 pt-0'>
              <div className='col-md-6'>
                <Form.Label>Pickup name *</Form.Label>
                <Form.Control value={pickupName} onChange={e => setPickupName(e.target.value)} required />
              </div>
              <div className='col-md-6'>
                <Form.Label>Pickup mobile *</Form.Label>
                <Form.Control value={pickupMobile} onChange={e => setPickupMobile(e.target.value)} required />
              </div>
            </div>
          </CellGroup>
        )}

        {/* Delivery address */}
        {!isPickup && (
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
        )}

        {/* Coupon */}
        {(coupons.length > 0 || couponError) && (
          <CellGroup>
            <Cell title='Coupon'>
              <Form.Select
                size='sm'
                value={selectedCouponId ?? ''}
                onChange={e => {
                  setSelectedCouponId(e.target.value ? Number(e.target.value) : null);
                  setCouponError(null);
                }}
              >
                <option value=''>No coupon ({couponCellValue})</option>
                {coupons.map(c => (
                  <option key={c.id} value={c.id} disabled={(c.min ?? 0) > cartLinesSubtotal}>
                    {c.name ? `${c.name} — ` : ''}−${c.discount} {c.min ? `(over $${c.min})` : ''}
                  </option>
                ))}
              </Form.Select>
            </Cell>
            {couponError && (
              <Alert variant='warning' className='m-3 mt-0 mb-3 d-flex justify-content-between align-items-center gap-2'>
                <span>{couponError}</span>
                <button
                  type='button'
                  className='btn btn-sm btn-outline-secondary flex-shrink-0'
                  onClick={() => {
                    setSelectedCouponId(null);
                    setCouponError(null);
                  }}
                >
                  Remove coupon
                </button>
              </Alert>
            )}
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
              { label: 'Goods total', value: money(totals?.goodsTotalPrice) },
              {
                label: 'Shipping',
                value: totalsLoading || !totals ? '…' : totals.freightPrice > 0 ? money(totals.freightPrice) : 'Free',
                variant: 'muted' as const,
              },
              // Wave-4 template breakdown: detail rows only — the charged figure
              // stays freightPrice (combine-mode max, not the breakdown sum).
              ...(!quoteLoading && !isPickup
                ? [...(quotes.local?.breakdown ?? []), ...(quotes.cj?.breakdown ?? [])].map(b => ({
                    label: `· ${b.templateName ?? (b.source === 'SYSTEM_FLAT' ? 'Standard shipping' : b.source ?? 'Shipping')}`,
                    value: `$${Number(b.amount ?? 0).toFixed(2)}${b.note ? ` — ${b.note}` : ''}`,
                    variant: 'muted' as const,
                  }))
                : []),
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
              ...((totals?.taxPrice ?? 0) > 0 ? [{ label: 'Tax', value: money(totals?.taxPrice) }] : []),
              ...((totals?.couponPrice ?? 0) > 0
                ? [{ label: 'Coupon', value: `−${money(totals?.couponPrice)}`, variant: 'success' as const }]
                : []),
              { label: 'Total', value: money(totals?.actualPrice), variant: 'total' },
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
              disabled={!cardAvailable}
              onChange={() => setPaymentMethod('CARD')}
            />
            {siteConfigLoaded && !cardAvailable && (
              // No publishable key ⇒ card payment is honestly unavailable. It is NOT
              // stubbed, and the customer is not told a placeholder authorisation "runs".
              <div className='small text-muted ms-4'>
                Card payment is unavailable right now — please use your wallet balance.
              </div>
            )}
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
            {paymentMethod === 'CARD' && cardAvailable && stripeElementsOptions && (
              <Elements stripe={stripePromiseFor(publishableKey!)} options={stripeElementsOptions}>
                <StripeCardForm ref={cardRef} disabled={submitting} />
              </Elements>
            )}
            {paymentMethod === 'CARD' && cardAvailable && !stripeElementsOptions && (
              <div className='text-muted small'>Preparing secure card payment…</div>
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
        {cardError && <Alert variant='danger'>{cardError}</Alert>}
        {/* Tax fails closed: we cannot price the cart, so we do not let it be ordered.
            Retryable — the provider being down is usually transient. */}
        {totalsBlocked && (
          <Alert variant='warning'>
            {totalsBlocked}{' '}
            <button type='button' className='btn btn-link p-0 align-baseline' onClick={() => dispatch(fetchCart())}>
              Retry
            </button>
          </Alert>
        )}
      </div>

      <SubmitBar
        total={totals?.actualPrice ?? 0}
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
        // No server total ⇒ nothing we are allowed to charge (tax fails closed).
        disabled={!checkoutValid || !totals || !!totalsBlocked || totalsLoading}
        loading={submitting}
      />
    </Page>
  );
};

export default CheckoutView;
