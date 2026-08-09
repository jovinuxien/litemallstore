import React from 'react';

import { money } from 'app/shared/util/money';

interface ProductPricingProps {
  retailPrice: number;
  counterPrice: number;
}

const ProductPricing: React.FC<ProductPricingProps> = ({ retailPrice, counterPrice }) => (
  <div className='mb-3'>
    <span className='fw-bold h5 me-2'>{money(retailPrice)}</span>
    <del className='small text-muted me-2'>{money(counterPrice)}</del>
    <span className='rounded p-1 bg-warning me-2 small'>−{money(counterPrice - retailPrice)}</span>
  </div>
);

export default ProductPricing;
