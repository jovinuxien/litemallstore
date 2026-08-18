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
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { priceNum } from 'app/components/userComponents/card/ProductCard';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loadSiteConfig } from 'app/shared/config/siteConfig';
import StripeCardForm, { StripeCardHandle } from 'app/shared/payment/StripeCardForm';
import { clearCart, fetchCart } from 'app/shared/reducers/cartSlice';
import { guestCheckoutThunk } from 'app/auth/customerAuthSlice';
import GoogleSignInButton from 'app/auth/GoogleSignInButton';
import AddressAutocompleteInput from 'app/components/commonComponents/AddressAutocompleteInput';
import PhoneInput from 'app/components/commonComponents/PhoneInput';
import OriginNote from 'app/components/commonComponents/OriginNote';
import RegionInput from 'app/components/commonComponents/RegionInput';
import { SHIPPING_COUNTRIES } from 'app/shared/data/countries';
import { regionsFor } from 'app/shared/data/regions';
import { trackBeginCheckout } from 'app/shared/tracking/ecommerce';
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
import { authApi, catalogApi, IAddress, ICombination, ICombinationPink, ICoupon, orderApi, promotionApi, userApi } from 'app/shared/api';
import { couponPickerLabel } from 'app/shared/util/couponFormat';
import { courierDeltaLabel, parseSelectlist, unusableReasonLabel, UnusableCoupon } from 'app/shared/util/checkoutHonesty';
import { money as fmtMoney } from 'app/shared/util/money';
import { originCountryName } from 'app/shared/util/euStock';
import { IFreightQuote, IStore } from 'app/shared/model/order/order.model';
import {
  Cell,
  CellGroup,
  EmptyState,
  GoodsLineCard,
  OrderSummary,
  Page,
  PageHead,
  PaymentBrandIcons,
  SubmitBar,
  AddressCard,
} from 'app/components/commonComponents/storefront';

import './Checkout.scss';

// Supported destination countries (CJ createOrder needs a real country + ISO code).
// Shared with the address book; the selected option supplies name + countryCode.
const COUNTRIES = SHIPPING_COUNTRIES;

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
 * Render a server-computed amount. `undefined` shows a placeholder rather than €0.00 —
 * "we haven't been told yet" and "it's free" must never look the same on a checkout.
 */
const money = (v?: number): string => (v == null ? '—' : fmtMoney(v));

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
  country: COUNTRIES.find(c => c.code === a.countryCode)?.name ?? '',
  countryCode: a.countryCode ?? '',
});

/** Map a typed checkout address onto the AddressSaveRequest/IAddress shape. */
const shippingToAddress = (s: ShippingInfo, countryCode?: string): IAddress => ({
  name: s.name,
  tel: s.mobile,
  province: s.region,
  city: s.kommune || s.region,
  county: s.kommune,
  addressDetail: [s.address, s.addressTwo].filter(Boolean).join(', '),
  postalCode: s.zip,
  countryCode: countryCode || undefined,
  isDefault: false,
});

/** The country a saved address carries, when it is one we ship to. */
const countryOfAddress = (a: IAddress | undefined): { name: string; code: string } | undefined =>
  COUNTRIES.find(c => c.code === a?.countryCode);

type StepState = 'locked' | 'open' | 'complete';

/**
 * One checkout step: numbered badge, title, and a body that COLLAPSES VIA CSS,
 * never unmounts — the payment step hosts the mounted Stripe Elements iframe,
 * and unmounting it between "Continue to review" and "Place order" would break
 * `cardRef.confirm()` (and the payment-retry path) at pay time.
 */
const StepSection: React.FC<{
  index: number;
  title: string;
  state: StepState;
  /** Collapsed one-glance recap shown once the step is complete. */
  summary?: React.ReactNode;
  onChange?: () => void;
  changeDisabled?: boolean;
  children?: React.ReactNode;
}> = ({ index, title, state, summary, onChange, changeDisabled, children }) => (
  <section id={`lm-step-${index}`} className={`lm-step lm-step--${state}`}>
    <header className='lm-step__head'>
      <span className='lm-step__badge'>{state === 'complete' ? <i className='bi bi-check-lg' /> : index}</span>
      <span className='lm-step__title'>{title}</span>
      {state === 'complete' && onChange && !changeDisabled && (
        <button type='button' className='lm-step__change' onClick={onChange}>
          Change
        </button>
      )}
    </header>
    {state === 'complete' && summary != null && <div className='lm-step__summary'>{summary}</div>}
    <div className='lm-step__body' hidden={state !== 'open'}>
      {children}
    </div>
  </section>
);

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
  // Wave-21 group-buy: `?pinkId=<own slot id>` rides the URL (refresh-safe)
  // from the PDP strip / /groupon/:id landing. Submit carries it and the ORDER
  // SERVICE prices the campaign line at the group price — the preview below
  // does not reflect it, so a banner states the group price from campaign data
  // and the server total governs. A stale/expired slot is REJECTED at submit
  // with a typed message, surfaced verbatim with a regular-price fallback.
  const [searchParams, setSearchParams] = useSearchParams();
  const pinkIdRaw = Number(searchParams.get('pinkId'));
  const groupPinkId = Number.isFinite(pinkIdRaw) && pinkIdRaw > 0 ? pinkIdRaw : null;

  const { cartList } = useAppSelector(state => state.cart.data);
  const { loading: orderLoading, errorMessage: orderError, phase } = useAppSelector(state => state.order);

  const [shipping, setShipping] = useState<ShippingInfo>(EMPTY_SHIPPING);
  // Per-country digit-count validity from the visible PhoneInput; true while no
  // phone field is on screen (saved address that already carries a number).
  const [phoneValid, setPhoneValid] = useState(true);
  const [paymentMethod, setPaymentMethod] = useState<CheckoutPaymentMethod>('CARD');
  const [message, setMessage] = useState('');
  // Destination country, kept separate so picking a saved address doesn't clear it.
  // Follows the phone dial-code country until the customer picks one explicitly
  // (or a saved address supplies its own).
  const [country, setCountry] = useState<{ name: string; code: string }>({ name: '', code: '' });
  const countryTouchedRef = useRef(false);
  // Orders created by submit, keyed by cart group — retained so a payment retry
  // pays the SAME order(s) and never re-submits an already-placed group.
  const [placed, setPlaced] = useState<{ local?: PlacedOrder; cj?: PlacedOrder }>({});
  const [addrError, setAddrError] = useState<string | null>(null);

  // Progressive checkout (Amazon-style): delivery → payment → review. `openStep`
  // is the one expanded step; `maxStep` is the furthest step reached, so "Change"
  // can reopen an earlier step without re-locking the ones after it.
  const [openStep, setOpenStep] = useState<1 | 2 | 3>(1);
  const [maxStep, setMaxStep] = useState<1 | 2 | 3>(1);
  const stepState = (i: 1 | 2 | 3): StepState => (i === openStep ? 'open' : i <= maxStep ? 'complete' : 'locked');
  const advanceTo = (s: 2 | 3) => {
    setMaxStep(m => (s > m ? s : m));
    setOpenStep(s);
    // After the collapse re-layout, bring the newly opened step into view.
    setTimeout(() => document.getElementById(`lm-step-${s}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 60);
  };

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
  // Wave-24.1: coupons the customer holds that DON'T apply here, with the
  // server's typed reason — rendered greyed so "coupons don't work" never
  // reads as silence. Empty against the pre-24.1 promotion service.
  const [unusableCoupons, setUnusableCoupons] = useState<UnusableCoupon[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);
  // Server-side coupon rejection at submit (e.g. redeemed elsewhere meanwhile),
  // surfaced inline at the picker rather than only as the page-level alert.
  const [couponError, setCouponError] = useState<string | null>(null);
  // Promo-code redemption (Wave 15): a code from an ad/mail redeems via the
  // existing /srv/coupon/exchange, then the usable list refreshes and the new
  // coupon is auto-selected when it applies to this cart.
  const [promoCode, setPromoCode] = useState('');
  const [promoBusy, setPromoBusy] = useState(false);
  const [promoNotice, setPromoNotice] = useState<{ ok: boolean; text: string } | null>(null);

  // Group-buy slot + campaign (display only — the group price is charged by
  // the order service at submit). null while absent or still resolving.
  const [groupSlot, setGroupSlot] = useState<{ pink: ICombinationPink; campaign: ICombination | null } | null>(null);
  // The typed stale-slot rejection from submit, rendered VERBATIM with a
  // "buy at regular price" fallback that drops the pinkId and retries.
  const [groupError, setGroupError] = useState<string | null>(null);

  useEffect(() => {
    if (groupPinkId == null) {
      setGroupSlot(null);
      return undefined;
    }
    let cancelled = false;
    promotionApi
      .combinationPink(groupPinkId)
      .then(async pink => {
        const campaign =
          pink?.combinationId != null ? await promotionApi.combinationDetail(pink.combinationId).catch(() => null) : null;
        if (!cancelled) setGroupSlot(pink?.pinkId != null ? { pink, campaign } : null);
      })
      .catch(() => {
        // Fail-soft: the banner just doesn't show campaign figures; submit
        // still carries the pinkId and the server stays the authority.
        if (!cancelled) setGroupSlot(null);
      });
    return () => {
      cancelled = true;
    };
  }, [groupPinkId]);

  /** Drop the group slot from this checkout and buy at the regular price. */
  const dropGroupSlot = () => {
    setGroupError(null);
    setGroupSlot(null);
    setSearchParams(
      prev => {
        prev.delete('pinkId');
        return prev;
      },
      { replace: true }
    );
  };

  // Contact email (Wave 15): the paid-order confirmation mail's recipient is
  // litemall_user.email, which registration leaves optional — an email-less
  // account was silently skipped by the mail pipeline. undefined = /auth/me not
  // resolved yet; null = resolved and MISSING ⇒ the required email block shows.
  const [accountEmail, setAccountEmail] = useState<string | null | undefined>(undefined);
  const [emailNotice, setEmailNotice] = useState<string | null>(null);
  // Account mobile, resolved alongside the email. Checkout collects the phone
  // in ONE place (the shipping address / pickup contact); when the profile has
  // no mobile yet it is backfilled from that single entry at submit — never
  // asked for twice, never overwritten if the account already has one.
  const [accountMobile, setAccountMobile] = useState<string | null>(null);

  // CJ lines ship via CJ Dropshipping, which requires a country + phone.
  const hasCjItems = useMemo(() => cartList.some(isCjItem), [cartList]);
  const hasLocalItems = useMemo(() => cartList.some(it => !isCjItem(it)), [cartList]);

  /**
   * Wave-28 per-line warehouse origin. One batched public read for the whole cart
   * (the coupon selectlist below collects goodsIds the same way), keyed by goodsId.
   *
   * Absent from the response ⇒ absent from the map ⇒ no note on that line. Any
   * failure leaves the map empty, so a backend without /srv/goods/origin — or one
   * that is briefly down — renders the checkout exactly as it looked before this
   * wave. An origin note is a nicety; it must never be able to break a checkout.
   */
  const [origins, setOrigins] = useState<Record<number, string>>({});
  const originIds = useMemo(
    () =>
      Array.from(new Set(cartList.map(it => Number(it.goodsId)).filter(id => Number.isFinite(id) && id > 0))).sort((a, b) => a - b),
    [cartList]
  );
  const originKey = originIds.join(',');
  useEffect(() => {
    if (originIds.length === 0) {
      setOrigins({});
      return;
    }
    let cancelled = false;
    catalogApi
      .goodsOrigin(originIds)
      .then(res => {
        if (cancelled) return;
        const map: Record<number, string> = {};
        (res?.list ?? []).forEach(row => {
          if (row?.goodsId != null && row.originCountry) map[Number(row.goodsId)] = row.originCountry;
        });
        setOrigins(map);
      })
      .catch(() => {
        if (!cancelled) setOrigins({});
      });
    return () => {
      cancelled = true;
    };
    // originKey is the stable identity of originIds — the array is rebuilt each render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [originKey]);

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

  // Resolve whether the account already has a contact email. A failed lookup
  // counts as missing: asking once too often beats silently skipping the
  // confirmation mail.
  useEffect(() => {
    authApi
      .me()
      .then(env => {
        const email = env.errno === 0 ? env.data?.email?.trim() : undefined;
        setAccountEmail(email ? email : null);
        const mobile = env.errno === 0 ? env.data?.mobile?.trim() : undefined;
        setAccountMobile(mobile || null);
      })
      .catch(() => setAccountEmail(null));
  }, []);

  // Funnel begin-checkout (Wave 15): once per checkout visit, with the display
  // subtotal — never the charged amount, which only the server computes. Fires
  // when the cart FIRST populates, not on mount: after a full-page load the
  // cart arrives async and a mount-only effect would drop the event.
  const beganCheckoutRef = useRef(false);
  useEffect(() => {
    if (!beganCheckoutRef.current && cartList.length > 0) {
      beganCheckoutRef.current = true;
      const subtotal = cartList.reduce((sum, it) => sum + priceNum(it.price) * (it.number ?? 0), 0);
      trackBeginCheckout(Number(subtotal.toFixed(2)), cartList.length);
    }
  }, [cartList]);

  // Freight/logistics quote per cart group (submit creates one order per group, each
  // charged its own freight). The CJ quote additionally carries the informational
  // carrier + delivery estimate once a destination country is picked.
  const [quotes, setQuotes] = useState<{ local?: IFreightQuote | null; cj?: IFreightQuote | null }>({});
  const [quoteLoading, setQuoteLoading] = useState(false);
  // Delivery-option chooser (V52): the CJ carrier the customer picked from the quote's
  // options list. Derived (not clobbered) on refresh: a pick that CJ stopped offering
  // simply falls back to the server's default line.
  const [selectedCjLogistic, setSelectedCjLogistic] = useState<string | null>(null);
  const cjOptions = quotes.cj?.cj?.options ?? [];
  const effectiveCjLogistic =
    selectedCjLogistic && cjOptions.some(o => o.logisticName === selectedCjLogistic)
      ? selectedCjLogistic
      : (quotes.cj?.cj?.logisticName ?? null);
  const effectiveCjAging =
    cjOptions.find(o => o.logisticName === effectiveCjLogistic)?.logisticAging ?? quotes.cj?.cj?.logisticAging;
  const cartSignature = useMemo(
    () => cartList.map(it => `${it.goodsId}:${it.productId ?? ''}:${it.number ?? 0}:${priceNum(it.price)}`).join('|'),
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
      const subtotalOf = (items: typeof cartList) => items.reduce((s, it) => s + priceNum(it.price) * (it.number ?? 0), 0);
      const next: { local?: IFreightQuote | null; cj?: IFreightQuote | null } = {};
      // Pickup charges no freight — skip the local quote entirely.
      if (localItems.length > 0 && !isPickup) {
        next.local = await orderApi
          .freightQuote({
            subtotal: subtotalOf(localItems),
            // Wave 4 freight templates: the selected address resolves the region
            // rule; cart lines drive per-template pricing.
            addressId: typeof selectedAddressId === 'number' ? selectedAddressId : undefined,
            items: localItems.map(it => ({ goodsId: it.goodsId, quantity: it.number ?? 1, price: priceNum(it.price) })),
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
          // Wave-24.1: the courier pick prices its upgrade delta in the
          // preview exactly as submit will — the total tracks the pick live.
          cjLogisticName: effectiveCjLogistic ?? undefined,
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
  }, [cartSignature, country.code, selectedAddressId, selectedCouponId, isPickup, effectiveCjLogistic]);

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
          const saved = countryOfAddress(def);
          if (saved) {
            countryTouchedRef.current = true;
            setCountry(saved);
          }
        } else {
          setSelectedAddressId('new');
        }
        // Pickup contact defaults to the number already on file — the phone is
        // asked for once (on the address); pickup merely reuses it, editable
        // for whoever actually collects the parcel.
        const knownTel = def?.tel?.trim();
        if (knownTel) setPickupMobile(prev => prev || knownTel);
      })
      .catch(() => setSelectedAddressId('new'));
  }, [dispatch]);

  // Usable-coupon query: the caller supplies the cart facts (promotion has no
  // cart access) — subtotal + the numeric goods ids. Re-runs when the cart
  // changes so threshold coupons appear/disappear with the total. Also called
  // by the promo-code Apply handler, which needs the refreshed list in hand.
  const queryUsableCoupons = async (): Promise<ICoupon[]> => {
    if (cartList.length === 0) {
      setUnusableCoupons([]);
      return [];
    }
    const amount = cartList.reduce((sum, it) => sum + priceNum(it.price) * (it.number ?? 0), 0);
    const goodsIds = cartList.map(it => Number(it.goodsId)).filter(id => Number.isFinite(id));
    try {
      // Wave-24.1 verbose split; parseSelectlist tolerates the legacy bare
      // array (pre-24.1 promotion), which lands everything in `usable`.
      const buckets = parseSelectlist(await userApi.couponSelectListVerbose(Number(amount.toFixed(2)), goodsIds));
      setUnusableCoupons(buckets.unusable);
      return buckets.usable;
    } catch {
      setUnusableCoupons([]);
      return [];
    }
  };

  useEffect(() => {
    queryUsableCoupons().then(setCoupons);
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
   * Display-only, and NOT money we charge: this drives the coupon picker's "spend €X more"
   * hint and the CJ/local group split. The charged figures all come from `totals`.
   */
  const cartLinesSubtotal = useMemo(
    () => cartList.reduce((sum, item) => sum + priceNum(item.price) * (item.number ?? 0), 0),
    [cartList]
  );

  const selectedCoupon = useMemo(() => coupons.find(c => c.id === selectedCouponId) ?? null, [coupons, selectedCouponId]);

  /**
   * Redeem a typed promo code, then refresh the usable list and auto-select the
   * newly claimed coupon when it applies to THIS cart. A code that redeems but
   * doesn't apply here (threshold/scope) says so honestly — it must not look
   * like it discounted the order.
   */
  const handleApplyPromo = async () => {
    const code = promoCode.trim();
    if (!code || promoBusy) return;
    setPromoBusy(true);
    setPromoNotice(null);
    try {
      await userApi.couponExchange(code);
      const prevIds = new Set(coupons.map(c => c.id));
      const list = await queryUsableCoupons();
      setCoupons(list);
      const claimed = list.find(c => !prevIds.has(c.id));
      if (claimed && (claimed.min ?? 0) <= cartLinesSubtotal) {
        setSelectedCouponId(claimed.id ?? null);
        setCouponError(null);
        setPromoNotice({ ok: true, text: 'Code applied — the coupon has been added to this order.' });
      } else {
        setPromoNotice({ ok: true, text: 'Code redeemed — the coupon is in your account, but it does not apply to this cart.' });
      }
      setPromoCode('');
    } catch (error) {
      setPromoNotice({ ok: false, text: messageOf(error, "That code couldn't be redeemed.") });
    } finally {
      setPromoBusy(false);
    }
  };

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
    countryTouchedRef.current = true;
    setCountry({ code, name: COUNTRIES.find(c => c.code === code)?.name ?? '' });
  };

  // The phone selector's country pre-fills the destination country (and with it
  // the region suggestions) until an explicit choice is made.
  const followPhoneCountry = (iso2: string) => {
    if (countryTouchedRef.current) return;
    const match = COUNTRIES.find(c => c.code === iso2);
    if (match) setCountry(prev => (prev.code === match.code ? prev : match));
  };

  const pickAddress = (a: IAddress) => {
    setSelectedAddressId(a.id ?? 'new');
    setShipping(addressToShipping(a));
    const saved = countryOfAddress(a);
    if (saved) {
      countryTouchedRef.current = true; // the address's own country is authoritative
      setCountry(saved);
    }
  };

  const usingNewAddress = selectedAddressId === 'new' || addresses.length === 0;
  // The ONE phone gap a saved-address checkout can have: an address-book entry
  // stored without a phone (the address service accepts a blank tel). Only then
  // does checkout ask for a number — and it is written BACK to that address at
  // submit, so it is asked exactly once, ever.
  const selectedSavedAddress = typeof selectedAddressId === 'number' ? addresses.find(a => a.id === selectedAddressId) : undefined;
  const savedAddressNeedsPhone = selectedSavedAddress != null && !(selectedSavedAddress.tel ?? '').trim();
  // Saved address is pre-validated; a new address needs the core fields. CJ orders
  // additionally need a phone and a destination country.
  const baseValid = usingNewAddress ? !!(shipping.name && shipping.address && shipping.region && shipping.zip) : selectedAddressId != null;
  // Only gate on phone validity while a PhoneInput is actually rendered — a
  // saved address with its own number leaves no field to correct.
  const phoneFieldVisible = usingNewAddress || savedAddressNeedsPhone;
  const addressValid =
    baseValid && (!phoneFieldVisible || phoneValid) && (!hasCjItems || !!(shipping.mobile && country.code));
  const savedAddressId = typeof selectedAddressId === 'number' ? selectedAddressId : null;
  // Pickup needs a store + contact instead of a delivery address.
  const pickupValid = selectedStoreId != null && !!pickupName && !!pickupMobile;
  // Accounts without a stored email must supply one — it is the confirmation
  // mail's recipient. `undefined` (lookup still in flight) doesn't block.
  const emailValid = accountEmail !== null || /^\S+@\S+\.\S+$/.test(shipping.email.trim());
  const checkoutValid = (isPickup ? pickupValid : addressValid) && emailValid;

  const couponCellValue =
    (totals?.couponPrice ?? 0) > 0
      ? `−${fmtMoney(totals!.couponPrice)}`
      : coupons.length > 0
        ? `${coupons.length} available`
        : 'None available';

  // Place each cart group (once) then pay, so a retry never creates a second order.
  const handlePlaceOrder = async () => {
    setAddrError(null);
    setCouponError(null);
    setCardError(null);
    setGroupError(null);

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
      if (!ok) {
        setOpenStep(2); // the inline reason renders inside the (collapsed) payment step
        return;
      }
    }

    // 1. Resolve a saved addressId for the local order, persisting a typed address.
    //    Pickup orders need no delivery address — the store is the destination.
    let addressId = savedAddressId;
    if (addressId == null && !isPickup) {
      if (!addressValid) return; // guarded by the disabled button below
      try {
        const newId = await userApi.addressSave(shippingToAddress(shipping, country.code));
        addressId = typeof newId === 'number' ? newId : Number(newId);
        if (!addressId) throw new Error('no id');
        setSelectedAddressId(addressId);
      } catch {
        setAddrError('Could not save the delivery address. Check the required fields and try again.');
        setOpenStep(1);
        return;
      }
    }

    // 1a. A saved address stored without a phone gets the number typed in the
    //     checkout's single phone field written back to it BEFORE submit — the
    //     order (and CJ placement) read the phone from litemall_address.tel
    //     server-side, so skipping this write would silently place a phone-less
    //     order and discard what the customer typed.
    if (!isPickup && selectedSavedAddress != null && savedAddressNeedsPhone && shipping.mobile.trim()) {
      const tel = shipping.mobile.trim();
      try {
        await userApi.addressSave({ ...selectedSavedAddress, tel });
        setAddresses(prev => prev.map(a => (a.id === selectedSavedAddress.id ? { ...a, tel } : a)));
      } catch {
        setAddrError('Could not save the phone number to your delivery address — please try again.');
        setOpenStep(1);
        return;
      }
    }

    // 1b. Land the contact email on the account (partial update) BEFORE placing,
    //     so the order-paid confirmation mail has a recipient. Best-effort: a
    //     server-side rejection warns inline but never blocks the purchase —
    //     losing the sale over the mail address would be worse than the skip.
    if (accountEmail === null) {
      const email = shipping.email.trim();
      try {
        const env = await authApi.profile({ email });
        if (env.errno === 0) {
          setAccountEmail(email);
          setEmailNotice(null);
        } else {
          setEmailNotice(env.errmsg ?? 'Could not save this email to your account — the order will still be placed.');
        }
      } catch {
        setEmailNotice('Could not save this email to your account — the order will still be placed.');
      }
    }

    // 1c. Same single-entry principle for the phone: the number captured on the
    //     address (or pickup contact) backfills litemall_user.mobile when the
    //     profile has none. Fully silent — the number already reached the order
    //     path above; a profile dedupe (705) or outage must not distract from
    //     the purchase, and an existing account mobile is never overwritten.
    const contactPhone = (isPickup ? pickupMobile : shipping.mobile).trim();
    if (!accountMobile && contactPhone) {
      try {
        const env = await authApi.profile({ mobile: contactPhone });
        if (env.errno === 0) setAccountMobile(contactPhone);
      } catch {
        /* silent by design */
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
    // The group-buy slot rides the submit of the group holding the campaign's
    // goods (falling back to the first group when the campaign is unresolved) —
    // the order service validates goodsId match and slot state either way.
    const campaignGoodsId = groupSlot?.campaign?.goodsId;
    const pinkGroup =
      groupPinkId == null
        ? null
        : (campaignGoodsId != null
            ? groups.find(g => g.items.some(it => Number(it.goodsId) === Number(campaignGoodsId)))?.group
            : undefined) ?? groups[0]?.group ?? null;
    for (const { group, items } of groups) {
      if (next[group]) continue;
      // eslint-disable-next-line no-await-in-loop
      // A coupon redeems once — it rides the first submitted order only.
      const couponRides = group === groups[0].group && selectedCoupon != null;
      const pinkRides = groupPinkId != null && group === pinkGroup;
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
          // V52: the carrier the customer picked in the delivery-option chooser.
          cjLogisticName: group === 'cj' ? (effectiveCjLogistic ?? undefined) : undefined,
          // Wave-21: the buyer's own group slot — the order service validates
          // it and prices the campaign line at the group price.
          pinkId: pinkRides ? groupPinkId : undefined,
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
        // A submit that carried the group slot and was rejected: surface the
        // server's typed message VERBATIM (e.g. "this group has expired —
        // start a new one or buy at regular price") with the regular-price
        // fallback action. The slice's own pre-submit rejections (401 sign-in,
        // 400 stale items) are not slot rejections.
        if (pinkRides && errno !== 400 && errno !== 401 && errmsg) {
          setGroupError(errmsg);
        }
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
          setOpenStep(2);
          return;
        }
        // eslint-disable-next-line no-await-in-loop
        const confirmedId = await cardRef.current?.confirm(intent.clientSecret);
        // null ⇒ StripeCardForm has rendered the reason inline; the order stays unpaid
        // and the customer can retry, which pays this same order rather than re-placing.
        if (!confirmedId) {
          setPlaced(next);
          setOpenStep(2); // the decline reason renders inline in the card form
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

  const deliveryComplete = (isPickup ? pickupValid : addressValid) && emailValid;
  const selectedStore = stores.find(s => s.id === selectedStoreId);

  // Collapsed-step recaps (Amazon's "Deliver to / Paying with" rows).
  const deliverySummary = isPickup ? (
    <>
      <div className='fw-semibold'>{pickupName || 'Store pickup'}</div>
      <div className='small text-muted'>
        Pickup at {selectedStore?.name}
        {selectedStore?.address ? ` — ${selectedStore.address}` : ''}
      </div>
    </>
  ) : (
    <>
      <div className='fw-semibold'>
        {(usingNewAddress ? shipping.name : selectedSavedAddress?.name) ?? ''}
        {(usingNewAddress ? shipping.mobile : selectedSavedAddress?.tel) && (
          <span className='text-muted fw-normal ms-2'>{usingNewAddress ? shipping.mobile : selectedSavedAddress?.tel}</span>
        )}
      </div>
      <div className='small text-muted'>
        {(usingNewAddress
          ? [shipping.address, shipping.addressTwo, shipping.kommune, shipping.region, shipping.zip, country.name]
          : [
              selectedSavedAddress?.addressDetail,
              selectedSavedAddress?.county,
              selectedSavedAddress?.city,
              selectedSavedAddress?.province,
              selectedSavedAddress?.postalCode,
              countryOfAddress(selectedSavedAddress)?.name,
            ]
        )
          .filter(Boolean)
          .join(', ')}
      </div>
      {accountEmail === null && shipping.email.trim() && <div className='small text-muted'>{shipping.email.trim()}</div>}
    </>
  );

  const paymentSummary = (
    <>
      <div className='d-flex align-items-center gap-2'>
        {paymentMethod === 'CARD' ? (
          <>
            Credit / debit card <PaymentBrandIcons />
          </>
        ) : (
          'Digital wallet (balance)'
        )}
      </div>
      {(totals?.couponPrice ?? 0) > 0 && <div className='small text-success'>Coupon applied — −{fmtMoney(totals!.couponPrice)}</div>}
    </>
  );

  /**
   * "Use this payment method": card details validate on leaving the step so a
   * typo is caught here rather than at place-order — the same elements.submit()
   * runs again inside handlePlaceOrder, which Stripe permits. With the card form
   * not yet mounted (totals still loading) the check simply defers to place-order.
   */
  const continueToReview = async () => {
    if (paymentMethod === 'CARD' && cardAvailable && !anyPlaced && cardRef.current) {
      const ok = await cardRef.current.validate();
      if (!ok) return;
    }
    advanceTo(3);
  };

  // Money rows — rendered once, in the sticky aside (desktop) / stacked summary (mobile).
  const summaryRows = [
    { label: 'Goods total', value: money(totals?.goodsTotalPrice) },
    {
      label: 'Shipping',
      value: totalsLoading || !totals ? '…' : totals.freightPrice > 0 ? money(totals.freightPrice) : 'Free',
      // Brand-teal highlight: shipping is the one summary line the customer can act
      // on (the delivery-option chooser below edits it), so it must not read as muted.
      variant: 'primary' as const,
    },
    // Wave-4 template breakdown: detail rows only — the charged figure
    // stays freightPrice (combine-mode max, not the breakdown sum).
    ...(!quoteLoading && !isPickup
      ? [...(quotes.local?.breakdown ?? []), ...(quotes.cj?.breakdown ?? [])].map(b => ({
          label: `· ${b.templateName ?? (b.source === 'SYSTEM_FLAT' ? 'Standard shipping' : b.source ?? 'Shipping')}`,
          value: `${fmtMoney(b.amount)}${b.note ? ` — ${b.note}` : ''}`,
          variant: 'muted' as const,
        }))
      : []),
    ...(shippingFee > 0 && (quotes.local?.freeShippingThreshold ?? quotes.cj?.freeShippingThreshold ?? 0) > 0
      ? [
          {
            label: 'Free shipping',
            value: `on orders over ${fmtMoney(quotes.local?.freeShippingThreshold ?? quotes.cj?.freeShippingThreshold)}`,
            variant: 'muted' as const,
          },
        ]
      : []),
    ...((totals?.taxPrice ?? 0) > 0 ? [{ label: 'Tax', value: money(totals?.taxPrice) }] : []),
    ...((totals?.couponPrice ?? 0) > 0
      ? [{ label: 'Coupon', value: `−${money(totals?.couponPrice)}`, variant: 'success' as const }]
      : []),
    // Wave-21: the group price is applied by the ORDER SERVICE at submit — the
    // preview cannot reflect it, so the summary carries an honest note
    // (campaign figure, no client-side price math).
    ...(groupPinkId != null && groupSlot?.campaign?.combinationPrice != null
      ? [
          {
            label: 'Group price',
            value: `${fmtMoney(priceNum(groupSlot.campaign.combinationPrice))}/item at payment`,
            variant: 'success' as const,
          },
        ]
      : []),
    { label: 'Total', value: money(totals?.actualPrice), variant: 'total' as const },
  ];

  const placeButtonText = submitting
    ? phase === 'paying'
      ? hasCjItems
        ? 'Processing payment… (dropship orders can take up to 30 seconds)'
        : 'Processing payment…'
      : 'Placing order…'
    : anyPlaced
      ? 'Retry payment'
      : 'Place order';
  // No server total ⇒ nothing we are allowed to charge (tax fails closed); the
  // review step must also have been reached before the order can be placed.
  const placeDisabled = !checkoutValid || !totals || !!totalsBlocked || totalsLoading || maxStep < 3;

  return (
    <Page>
      <PageHead title='Checkout' />
      <div className='container lm-checkout'>
        <div className='row g-3'>
        <div className='col-lg-8'>
        {/* Wave-21 group order banner. The preview totals below do NOT reflect
            the group price — the order service applies it at submit — so this
            states the campaign figure (server data, no client math) and the
            server total governs. */}
        {groupPinkId != null && !groupError && (
          <Alert variant='info' className='d-flex justify-content-between align-items-center gap-2 flex-wrap'>
            <span>
              <i className='bi bi-people-fill me-1' /> <strong>Group order</strong>
              {groupSlot?.campaign?.combinationPrice != null ? (
                <>
                  {' '}
                  — group price applied at payment: <strong>{fmtMoney(priceNum(groupSlot.campaign.combinationPrice))}</strong> per item
                  {groupSlot.campaign.title ? <> ({groupSlot.campaign.title})</> : null}. The total below may show the regular price
                  until then.
                </>
              ) : (
                <> — the group price for your slot is applied at payment; the total below may show the regular price until then.</>
              )}
            </span>
            <button type='button' className='btn btn-sm btn-outline-secondary' onClick={dropGroupSlot} disabled={anyPlaced}>
              Buy at regular price instead
            </button>
          </Alert>
        )}
        {/* STEP 1 — delivery. Collapses to a "deliver to" recap once complete;
            frozen (no Change) while a placed order awaits a payment retry. */}
        <StepSection
          index={1}
          title='Delivery'
          state={stepState(1)}
          summary={deliverySummary}
          onChange={() => setOpenStep(1)}
          changeDisabled={anyPlaced}
        >
        {/* Delivery method (Wave 4 pickup — only offered when the order service
            has stores AND the cart is all-local; CJ lines always ship). */}
        {stores.length > 0 && !hasCjItems && (
          <div className='p-3 pb-0'>
            <div className='small fw-semibold text-muted mb-2'>Delivery method</div>
            <div className='d-flex gap-4'>
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
          </div>
        )}

        {/* Pickup: store picker + pickup contact replace the address book. */}
        {isPickup && (
          <div>
            <div className='small fw-semibold text-muted p-3 pb-0'>Pickup store</div>
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
          </div>
        )}

        {/* Delivery address */}
        {!isPickup && (
        <div>
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
                {/* THE phone field of this checkout: stored on the address
                    (litemall_address.tel), copied to the order server-side and
                    backfilled onto the profile at submit. Dial-code + E.164 so
                    checkout stores the same shape as register/address book.
                    Its dial-code country also pre-fills the destination country
                    (and the region suggestions) until one is picked explicitly. */}
                <PhoneInput
                  value={shipping.mobile}
                  onChange={m => setShipping(prev => ({ ...prev, mobile: m }))}
                  onCountryChange={followPhoneCountry}
                  onValidityChange={setPhoneValid}
                />
              </div>
              <div className='col-md-6'>
                <Form.Label>Country{hasCjItems ? ' *' : ''}</Form.Label>
                <Form.Select value={country.code} onChange={handleCountryChange} required={hasCjItems}>
                  <option value=''>-- Country --</option>
                  {COUNTRIES.map(c => (
                    <option key={c.code} value={c.code}>
                      {c.name}
                    </option>
                  ))}
                </Form.Select>
              </div>
              <div className='col-12'>
                <Form.Label>Address line 1 *</Form.Label>
                {/* Wave 16: env-gated Places suggestions scoped to the
                    destination country when one is picked; unset key ⇒ the
                    same plain input as before. */}
                <AddressAutocompleteInput
                  name='address'
                  value={shipping.address}
                  onChange={text => setShipping(prev => ({ ...prev, address: text }))}
                  onResolved={parts => {
                    setShipping(prev => ({
                      ...prev,
                      address: parts.line,
                      kommune: parts.city ?? prev.kommune,
                      region: parts.region ?? prev.region,
                      zip: parts.postalCode ?? prev.zip,
                    }));
                    // A resolved place knows its country better than any default.
                    const resolved = COUNTRIES.find(c => c.code === parts.countryCode);
                    if (resolved) {
                      countryTouchedRef.current = true;
                      setCountry(resolved);
                    }
                  }}
                  countryCode={country.code || undefined}
                  required
                />
              </div>
              <div className='col-12'>
                <Form.Label>Address line 2</Form.Label>
                <Form.Control name='addressTwo' value={shipping.addressTwo} onChange={handleInputChange} />
              </div>
              <div className='col-md-4'>
                <Form.Label>Region / State *</Form.Label>
                {/* Type-ahead over the selected country's regions (static data);
                    free text stays valid for anything outside the list. */}
                <RegionInput
                  name='region'
                  value={shipping.region}
                  onChange={text => setShipping(prev => ({ ...prev, region: text }))}
                  suggestions={regionsFor(country.code)}
                  placeholder={country.code ? 'Select or type a region' : 'Region'}
                  required
                />
              </div>
              <div className='col-md-4'>
                <Form.Label>City / Kommune</Form.Label>
                <Form.Control name='kommune' value={shipping.kommune} onChange={handleInputChange} placeholder='City' />
              </div>
              <div className='col-md-4'>
                <Form.Label>Zip *</Form.Label>
                <Form.Control name='zip' value={shipping.zip} onChange={handleInputChange} required />
              </div>
            </div>
          )}

          {/* CJ Dropshipping needs a destination country + phone regardless of which
              address is used. The country select renders here only for the
              saved-address path — a new address already carries the field above. */}
          {hasCjItems && (
            <div className='px-3 pb-3'>
              {/* Wave-28: this used to name our internal supplier ("CJ Dropshipping") to
                  the customer, which told them nothing useful about their delivery and
                  named the wrong party besides. The ask it actually exists to make —
                  country + phone, which the carrier needs — is kept; per-item origin is
                  now stated on the lines themselves, from measurement. */}
              <Alert variant='info' className='mb-2'>
                These items ship direct from the warehouse — please provide a <strong>country</strong> and a{' '}
                <strong>phone number</strong> so the carrier can deliver.
              </Alert>
              {!usingNewAddress && (
                <>
                  <Form.Label>Destination country *</Form.Label>
                  <Form.Select value={country.code} onChange={handleCountryChange} required>
                    <option value=''>-- Country --</option>
                    {COUNTRIES.map(c => (
                      <option key={c.code} value={c.code}>
                        {c.name}
                      </option>
                    ))}
                  </Form.Select>
                </>
              )}
              {/* Informational logistics line for the CJ group (carrier + delivery estimate). */}
              {country.code && (
                <div className='mt-2 small'>
                  {quoteLoading ? (
                    <span className='text-muted'>Checking logistics…</span>
                  ) : effectiveCjLogistic ? (
                    <span>
                      <i className='bi bi-truck me-1' />
                      Ships via <strong>{effectiveCjLogistic}</strong>
                      {effectiveCjAging ? <> · estimated delivery {effectiveCjAging} days</> : null}
                      {cjOptions.length > 1 && <span className='text-muted'> · more options in the order summary</span>}
                    </span>
                  ) : quotes.cj?.cjNote ? (
                    <span className='text-muted'>{quotes.cj.cjNote}</span>
                  ) : null}
                </div>
              )}
              {/* One phone per checkout: a saved address normally brings its
                  own number (shown on the card above — no second field). Only
                  an address saved WITHOUT a phone prompts here, and the number
                  is written back to that address at submit. The old always-on
                  duplicate field silently discarded its edits. */}
              {!usingNewAddress && savedAddressNeedsPhone && (
                <div className='mt-2'>
                  <Form.Label>Phone *</Form.Label>
                  <PhoneInput
                    key={selectedSavedAddress?.id ?? 'none'}
                    value={shipping.mobile}
                    onChange={m => setShipping(prev => ({ ...prev, mobile: m }))}
                    defaultIso2={country.code || undefined}
                    onValidityChange={setPhoneValid}
                  />
                  <div className='form-text'>Your selected address has no phone number yet — we&apos;ll save this one to it.</div>
                </div>
              )}
            </div>
          )}
        </div>
        )}

        {/* Contact email (Wave 15) — only when the account has none on file.
            Renders on BOTH address paths (saved and new): it is the order-mail
            recipient, not part of the delivery address. */}
        {accountEmail === null && (
          <div>
            <div className='p-3'>
              <Form.Label>Email for order updates *</Form.Label>
              <Form.Control
                name='email'
                type='email'
                value={shipping.email}
                onChange={handleInputChange}
                placeholder='you@example.com'
                required
              />
              <div className='form-text'>We&apos;ll send your order confirmation and shipping updates here.</div>
              {emailNotice && (
                <div className='small text-danger mt-1' role='status'>
                  {emailNotice}
                </div>
              )}
            </div>
          </div>
        )}

        <div className='lm-step__continue'>
          <button type='button' className='btn btn-lm-primary' disabled={!deliveryComplete} onClick={() => advanceTo(2)}>
            Continue to payment
          </button>
          {!deliveryComplete && <div className='form-text mt-1'>Fill in the delivery details above to continue.</div>}
        </div>
        </StepSection>

        {/* STEP 2 — payment method + discounts. Locked until delivery completes,
            so the card form always mounts against a destination-priced total. */}
        <StepSection index={2} title='Payment' state={stepState(2)} summary={paymentSummary} onChange={() => setOpenStep(2)}>
          <Cell>
            <Form.Check
              type='radio'
              id='pay-card'
              name='paymentMethod'
              label={
                <>
                  Credit / debit card
                  <PaymentBrandIcons muted={!cardAvailable} />
                </>
              }
              checked={paymentMethod === 'CARD'}
              disabled={!cardAvailable}
              onChange={() => setPaymentMethod('CARD')}
            />
            {siteConfigLoaded && !cardAvailable && (
              // No publishable key ⇒ card payment is honestly unavailable. It is NOT
              // stubbed, and the customer is not told a placeholder authorisation "runs".
              <div className='small text-muted ms-4'>
                Card payment is temporarily unavailable — please check back soon.
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

        {/* Coupon + promo code. Always renders: a customer holding a
            code from an ad/mail must be able to enter it even with zero claimed
            coupons. */}
        <div>
            {coupons.length > 0 && (
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
                {/* Wave 18: selectlist `discount` is the server-COMPUTED dollar
                    discount for this cart (percent coupons included) — the
                    label renders dollars plus the coupon kind. */}
                {coupons.map(c => (
                  <option key={c.id} value={c.id} disabled={(c.min ?? 0) > cartLinesSubtotal}>
                    {couponPickerLabel(c)}
                  </option>
                ))}
                {/* Wave-24.1: held-but-unusable coupons stay VISIBLE, greyed,
                    with the server's typed reason — never a silent vanish. */}
                {unusableCoupons.map(c => (
                  <option key={`u-${c.id}`} value='' disabled>
                    {couponPickerLabel(c)} — {unusableReasonLabel(c.reason, c.minGap)}
                  </option>
                ))}
              </Form.Select>
            </Cell>
            )}
            <div className='px-3 py-2 d-flex gap-2'>
              <Form.Control
                size='sm'
                placeholder='Promo code'
                aria-label='Promo code'
                value={promoCode}
                onChange={e => {
                  setPromoCode(e.target.value);
                  if (promoNotice) setPromoNotice(null);
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    handleApplyPromo();
                  }
                }}
              />
              <button
                type='button'
                className='btn btn-sm btn-outline-primary flex-shrink-0'
                disabled={!promoCode.trim() || promoBusy}
                onClick={handleApplyPromo}
              >
                {promoBusy ? 'Applying…' : 'Apply'}
              </button>
            </div>
            {promoNotice && (
              <div className={`px-3 pb-2 small ${promoNotice.ok ? 'text-success' : 'text-danger'}`} role='status'>
                {promoNotice.text}
              </div>
            )}
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
        </div>

        <div className='lm-step__continue'>
          <button type='button' className='btn btn-lm-primary' onClick={continueToReview}>
            Continue to review
          </button>
        </div>
        </StepSection>

        {/* STEP 3 — review the items and place the order. */}
        <StepSection index={3} title={`Review items (${cartList.length})`} state={stepState(3)} onChange={() => setOpenStep(3)}>
          <div>
          {cartList.map(item => (
            <GoodsLineCard
              key={item.id}
              picUrl={item.picUrl}
              name={item.goodsName}
              to={item.goodsId ? `/product/${item.goodsId}` : undefined}
              specs={item.specifications}
              price={priceNum(item.price) * (item.number ?? 0)}
              qty={item.number ?? 0}
              note={<OriginNote countryCode={origins[Number(item.goodsId)]} />}
            />
          ))}
          </div>

          {/* Order note */}
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
        </StepSection>

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
        {/* Wave-21 stale-slot rejection — the order service's typed message,
            verbatim, with the regular-price fallback. Replaces the generic
            order alert (same message) while active. */}
        {groupError && (
          <Alert variant='danger' className='d-flex justify-content-between align-items-center gap-2 flex-wrap'>
            <span>{groupError}</span>
            <button type='button' className='btn btn-sm btn-outline-light border' onClick={dropGroupSlot}>
              Buy at regular price
            </button>
          </Alert>
        )}
        {addrError && <Alert variant='danger'>{addrError}</Alert>}
        {orderError && !groupError && <Alert variant='danger'>{orderError}</Alert>}
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

        {/* Sticky order summary — on desktop the place-order button lives here;
            on mobile the aside stacks below the steps and the sticky bottom bar
            carries the button instead. */}
        <div className='col-lg-4'>
          <aside className='lm-checkout__aside'>
            <div className='lm-checkout__aside-title'>Order summary</div>
            <OrderSummary rows={summaryRows} />
            {/* Delivery option (V52) — the chosen option highlighted in the brand
                teal; CJ carts pick among every carrier CJ offers (more offerings
                appear here automatically as they become available). */}
            <div className='lm-delivery'>
              <div className='lm-delivery__head'>
                <span>Delivery option</span>
                {!anyPlaced && (
                  <button type='button' className='btn btn-link btn-sm p-0' onClick={() => setOpenStep(1)}>
                    Edit
                  </button>
                )}
              </div>
              {isPickup ? (
                <div className='lm-delivery__option lm-delivery__option--selected'>
                  <i className='bi bi-shop' />
                  <div>
                    <div className='lm-delivery__name'>Store pickup</div>
                    <div className='lm-delivery__meta'>Free — collect at the selected store</div>
                  </div>
                  <i className='bi bi-check-circle-fill lm-delivery__check' />
                </div>
              ) : quoteLoading ? (
                <div className='lm-delivery__meta py-1'>Checking delivery options…</div>
              ) : (
                <>
                  {hasLocalItems && (
                    <div className='lm-delivery__option lm-delivery__option--selected'>
                      <i className='bi bi-box-seam' />
                      <div>
                        <div className='lm-delivery__name'>Standard shipping</div>
                        <div className='lm-delivery__meta'>
                          {(quotes.local?.freightPrice ?? 0) > 0
                            ? fmtMoney(quotes.local?.freightPrice)
                            : 'Free'}
                          {hasCjItems ? ' — items shipped from our store' : ''}
                        </div>
                      </div>
                      {!hasCjItems && <i className='bi bi-check-circle-fill lm-delivery__check' />}
                    </div>
                  )}
                  {hasCjItems &&
                    (cjOptions.length > 0 ? (
                      cjOptions.map(o => {
                        const isSelected = o.logisticName === effectiveCjLogistic;
                        // Wave-24.1: the server's upgradeDelta rendered verbatim
                        // ("Included" / "+€x.xx"); null/absent ⇒ no label
                        // (unpriceable line, or the pre-24.1 order half).
                        const deltaLabel = courierDeltaLabel(o.upgradeDelta);
                        return (
                          <button
                            type='button'
                            key={o.logisticName}
                            className={`lm-delivery__option${isSelected ? ' lm-delivery__option--selected' : ''}`}
                            onClick={() => setSelectedCjLogistic(o.logisticName ?? null)}
                            disabled={anyPlaced}
                            aria-pressed={isSelected}
                          >
                            <i className='bi bi-truck' />
                            <div>
                              <div className='lm-delivery__name'>
                                {o.logisticName}
                                {/* Wave-28: flag the couriers quoted from an EU warehouse.
                                    Non-EU lines get no counter-label — the absence is the
                                    signal, same as upgradeDelta's null. */}
                                {originCountryName(o.originCountry) && (
                                  <span className='lm-delivery__origin'>EU · {originCountryName(o.originCountry)}</span>
                                )}
                              </div>
                              {o.logisticAging && <div className='lm-delivery__meta'>Estimated delivery {o.logisticAging} days</div>}
                            </div>
                            {deltaLabel && (
                              <span className={`lm-delivery__delta${deltaLabel === 'Included' ? ' lm-delivery__delta--included' : ''}`}>
                                {deltaLabel}
                              </span>
                            )}
                            {isSelected && <i className='bi bi-check-circle-fill lm-delivery__check' />}
                          </button>
                        );
                      })
                    ) : (
                      <div className='lm-delivery__meta py-1'>
                        {country.code
                          ? (quotes.cj?.cjNote ?? 'Delivery options appear once we can quote your destination.')
                          : 'Pick a destination country to see delivery options.'}
                      </div>
                    ))}
                </>
              )}
            </div>
            <button
              type='button'
              className='btn btn-lm-primary lm-checkout__aside-action d-none d-lg-block'
              onClick={handlePlaceOrder}
              disabled={placeDisabled || submitting}
            >
              {submitting && <span className='spinner-border spinner-border-sm me-2' role='status' aria-hidden='true' />}
              {placeButtonText}
            </button>
            <div className='lm-checkout__aside-note d-none d-lg-block'>
              <i className='bi bi-lock-fill me-1' />
              Secure checkout — nothing is charged until you place the order.
            </div>
          </aside>
        </div>
        </div>
      </div>

      <SubmitBar
        className='d-lg-none'
        total={totals?.actualPrice ?? 0}
        buttonText={placeButtonText}
        onSubmit={handlePlaceOrder}
        disabled={placeDisabled}
        loading={submitting}
      />
    </Page>
  );
};

/**
 * Wave 16: logged-out checkout gate — guest email, sign-in, or Google, in
 * place of the old redirect-to-login wall. A successful guest/Google auth
 * flips `isAuthenticated`, which mounts the real checkout fresh (all fetches
 * run with the new session). The local cart survives in redux either way.
 */
const GuestCheckoutGate: React.FC = () => {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { cartList } = useAppSelector(state => state.cart.data);
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [gateError, setGateError] = useState<{ hasAccount: boolean; text: string } | null>(null);

  const emailOk = /^\S+@\S+\.\S+$/.test(email.trim());

  const continueAsGuest = async () => {
    if (!emailOk || busy) return;
    setBusy(true);
    setGateError(null);
    const result = await dispatch(guestCheckoutThunk({ email: email.trim() }));
    if (!guestCheckoutThunk.fulfilled.match(result)) {
      const { errno = -1, errmsg = '' } = (result.payload as { errno?: number; errmsg?: string } | undefined) ?? {};
      setGateError({
        hasAccount: errno === 706,
        text: errno === 706 ? 'This email already has an account — please sign in to continue.' : errmsg || 'Guest checkout failed — please try again.',
      });
      setBusy(false);
    }
    // fulfilled: isAuthenticated flips and the parent mounts the real checkout.
  };

  return (
    <Page>
      <div className='container my-4' style={{ maxWidth: 480 }}>
        <h1 className='h4 mb-3'>Checkout</h1>
        {cartList.length === 0 && (
          <Alert variant='light' className='border'>
            Your cart is empty. <Link to='/'>Continue shopping</Link>
          </Alert>
        )}
        <CellGroup>
          <div className='p-3'>
            <div className='fw-semibold mb-2'>Continue as guest</div>
            <Form.Label>Email *</Form.Label>
            <Form.Control
              type='email'
              value={email}
              onChange={e => {
                setEmail(e.target.value);
                if (gateError) setGateError(null);
              }}
              onKeyDown={e => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  continueAsGuest();
                }
              }}
              placeholder='you@example.com'
              autoFocus
            />
            <div className='form-text'>Your order confirmation goes here. No password needed — you can create one after buying.</div>
            {gateError && (
              <div className='small text-danger mt-1' role='status'>
                {gateError.text}{' '}
                {gateError.hasAccount && (
                  <Link to='/login' state={{ from: { pathname: '/checkout' } }}>
                    Sign in
                  </Link>
                )}
              </div>
            )}
            <button type='button' className='btn btn-lm-primary w-100 mt-2' disabled={!emailOk || busy} onClick={continueAsGuest}>
              {busy ? 'One moment…' : 'Continue as guest'}
            </button>
          </div>
          <div className='px-3 pb-3'>
            <div className='text-center text-muted small my-2'>— or —</div>
            <GoogleSignInButton />
            <button
              type='button'
              className='btn btn-lm-outline w-100'
              onClick={() => navigate('/login', { state: { from: { pathname: '/checkout' } } })}
            >
              Sign in to your account
            </button>
          </div>
        </CellGroup>
      </div>
    </Page>
  );
};

/** Route entry: authenticated customers (and provisioned guests) see the real checkout. */
const CheckoutGate: React.FC = () => {
  const { isAuthenticated } = useAppSelector(state => state.customerAuth.data);
  return isAuthenticated ? <CheckoutView /> : <GuestCheckoutGate />;
};

export default CheckoutGate;
