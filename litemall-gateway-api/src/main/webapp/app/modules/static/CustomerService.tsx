import React from 'react';
import { Link } from 'react-router-dom';

/**
 * Customer service, modelled on litemall-vue `user/module-server`. Static
 * contact information (no backend).
 */
const CustomerService: React.FC = () => (
  <div className='container my-4' style={{ maxWidth: 640 }}>
    <h1 className='h4 mb-3'>Customer service</h1>
    <ul className='list-group'>
      <li className='list-group-item d-flex align-items-center gap-3'>
        <i className='bi bi-headset fs-4 text-primary' />
        <div>
          <div className='fw-semibold'>Online support</div>
          <div className='text-muted small'>Mon–Fri, 9:00–18:00</div>
        </div>
      </li>
      <li className='list-group-item d-flex align-items-center gap-3'>
        <i className='bi bi-telephone fs-4 text-primary' />
        <div>
          <div className='fw-semibold'>Phone</div>
          <div className='text-muted small'>+1 (800) 000-0000</div>
        </div>
      </li>
      <li className='list-group-item d-flex align-items-center gap-3'>
        <i className='bi bi-envelope fs-4 text-primary' />
        <div>
          <div className='fw-semibold'>Email</div>
          <div className='text-muted small'>support@trovemo.com</div>
        </div>
      </li>
    </ul>
    <p className='mt-4 text-muted'>
      Have a suggestion? <Link to='/user/feedback'>Send us feedback</Link>.
    </p>
  </div>
);

export default CustomerService;
