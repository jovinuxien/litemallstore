import React from 'react';

interface ProductPricingProps {
  retailPrice: number;
  counterPrice: number;
}

const ProductPricing: React.FC<ProductPricingProps> = ({ retailPrice, counterPrice }) => (
  <div className='mb-3'>
    <span className='fw-bold h5 me-2'>${retailPrice}</span>
    <del className='small text-muted me-2'>${counterPrice}</del>
    <span className='rounded p-1 bg-warning me-2 small'>-${counterPrice - retailPrice}</span>
  </div>
);

export default ProductPricing;
