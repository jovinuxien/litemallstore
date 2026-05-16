import { IIssue } from 'app/shared/model/product/product.model';
import React from 'react';

interface ProductDetailCommentsProps {
  data: IIssue[];
}

const ProductInfo: React.FC<ProductDetailCommentsProps> = ({ data }) => {
  return (
    <>
      <h1 className='h5 d-inline me-2' style={{ fontWeight: 'bold', fontFamily: 'Helvetica Neue, sans-serif' }}>
        {data.map(issue => (
          <span key={issue.id}>{issue.question}</span>
        ))}
      </h1>

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
