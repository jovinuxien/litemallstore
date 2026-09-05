import React from 'react';
import { Link } from 'react-router-dom';

/**
 * How payments work — the page behind the footer's "Secure payments" promise.
 *
 * Every claim here is checkable in litemall-order:
 *  - Stripe PaymentIntent with automatic payment methods (so SCA / 3-D Secure is
 *    handled on that path), charged in the configured currency (eur in prod).
 *  - StripePaymentGatewayAdapter.verify() asserts status == "succeeded", that
 *    amount_received equals the expected minor units exactly, and that the
 *    intent's orderId metadata matches — with NO fallback, because "a stub id
 *    here is indistinguishable from a real payment later".
 *  - Refunds: adapter .refund() -> Stripe Refund.create with an idempotency key;
 *    a provider rejection throws and rolls back, leaving the order in
 *    REFUND_REQUEST rather than falsely reading REFUNDED.
 *
 * ⚠ NO payment method is named beyond card and store balance. The adapter enables
 * Stripe's automatic payment methods, so whichever methods are switched on in the
 * Stripe Dashboard appear at checkout — that is a dashboard fact this codebase
 * cannot see. Naming Klarna/iDEAL/SEPA here without confirming them would be the
 * same unverified promise the footer used to make.
 */
const Payments: React.FC = () => (
  <div className='container my-4 lm-doc' style={{ maxWidth: 720 }}>
    <h1 className='mb-3'>How payments work</h1>

    <h2 className='mt-4'>Who handles your card</h2>
    <p>
      Payments are processed by <strong>Stripe</strong>. Your card details are entered into Stripe&apos;s own
      payment form and go straight to Stripe — they never reach Trovemo&apos;s servers, and we never see or
      store your card number.
    </p>

    <h2 className='mt-4'>Your bank&apos;s verification step</h2>
    <p>
      Where your bank requires it, Stripe runs the &ldquo;confirm it&apos;s you&rdquo; check as part of the
      payment. That is a legal requirement for online card payments in the EU, and it protects you as much
      as us.
    </p>

    <h2 className='mt-4'>What you are charged</h2>
    <p>
      Everything on Trovemo is priced and charged in <strong>euro</strong>. The amount you approve is the
      amount taken — no fees appear at the end. Shipping, and any discount, are shown before you pay; see{' '}
      <Link to='/delivery'>Shipping &amp; delivery</Link>.
    </p>

    <h2 className='mt-4'>We verify the payment before accepting the order</h2>
    <p>
      When you pay, we do not take your browser&apos;s word for it. Our server asks Stripe directly, and
      marks the order paid only if the payment genuinely succeeded, <strong>the amount received matches the
      order to the cent</strong>, and the payment belongs to that order. Anything else is refused. That is
      why confirmation can take a moment — we are checking the payment, not just displaying it.
    </p>

    <h2 className='mt-4'>Paying from your balance</h2>
    <p>
      If you hold a Trovemo balance you can pay with it instead of a card. Refunds on a balance-paid order
      go back to your balance.
    </p>

    <h2 className='mt-4'>Refunds</h2>
    <p>
      Approved refunds go back the way you paid: card payments are reversed through Stripe automatically,
      balance payments are credited back. A card refund then takes a few business days to appear, depending
      on your bank. The full policy is on <Link to='/returns'>Returns &amp; Refunds</Link>.
    </p>

    <h2 className='mt-4'>What we keep</h2>
    <p>
      We store your order, what you bought, your delivery address, and a reference to the payment. Not your
      card number. What else we hold, and why, is set out in our <Link to='/privacy'>Privacy Policy</Link>.
    </p>

    <h2 className='mt-4'>If something looks wrong</h2>
    <p>
      A card that is declined is not charged, and a pending amount your bank shows against a declined
      attempt is an authorisation that clears on its own. If an amount looks wrong, or you see a charge for
      an order that was not accepted, email <Link to='/service'>customer service</Link> with your order
      number — we can see exactly what was taken and put it right.
    </p>
  </div>
);

export default Payments;
