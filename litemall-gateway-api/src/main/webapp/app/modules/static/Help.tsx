import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Help center, modelled on litemall-vue `user/module-help`. Static FAQ content
 * (no backend). Mirrors the reference storefront's help topics.
 */
const FAQ = [
  { q: 'How do I place an order?', a: 'Add items to your cart, then go to checkout, choose a delivery address and a payment method, and place the order.' },
  { q: 'Which payment methods are supported?', a: 'Credit/debit card and your digital wallet balance.' },
  { q: 'How do I track my order?', a: 'Open “My orders” from the account menu to see each order’s status and details.' },
  { q: 'What is the return policy?', a: 'Most items can be returned within 7 days. Start a request from the order’s after-sales section.' },
  { q: 'How do I use a coupon?', a: 'Available coupons appear at checkout and on eligible product pages. Pick one to apply the discount.' },
];

const Help: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 720 }}>
    <h1 className='h4 mb-3'>Help center</h1>
    <div className='accordion-flush'>
      {FAQ.map((f, i) => (
        <div key={i} className='border-bottom py-3'>
          <div className='fw-semibold mb-1'>
            <i className='bi bi-question-circle me-2 text-primary' />
            {f.q}
          </div>
          <div className='text-muted'>{f.a}</div>
        </div>
      ))}
    </div>
    <p className='mt-4 text-muted'>
      Still need help? <Link to='/service'>Contact customer service</Link> or <Link to='/user/feedback'>send feedback</Link>.
    </p>
  </div>
);

export default Help;
