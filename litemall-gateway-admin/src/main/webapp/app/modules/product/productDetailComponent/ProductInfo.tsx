import React from 'react';

interface ProductInfoProps {
  info: {
    brief: string;
    isNew: boolean;
    isHot: boolean;
    isOnSale: boolean;
  };
}

const ProductInfo: React.FC<ProductInfoProps> = ({ info }) => {
  return (
    <>
      <h1 className='h5 d-inline me-2' style={{ fontWeight: 'bold', fontFamily: 'Helvetica Neue, sans-serif' }}>
        {info.brief}
      </h1>
      <span className='badge bg-success me-2'>{info.isNew ?? ''}</span>
      <span className='badge bg-danger me-2'>{info.isHot ?? ''}</span>
      <div className='mb-3'>
        <i className='bi bi-star-fill text-warning me-1' />
        <i className='bi bi-star-fill text-warning me-1' />
        <i className='bi bi-star-fill text-warning me-1' />
        <i className='bi bi-star-fill text-warning me-1' />
        <i className='bi bi-star-fill text-secondary me-1' />| <span className='text-muted small'>42 ratings and 4 reviews</span>
      </div>
    </>
  );
};

export default ProductInfo;
