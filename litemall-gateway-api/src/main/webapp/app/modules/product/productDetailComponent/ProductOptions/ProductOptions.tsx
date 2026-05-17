import { IAttribute } from 'app/shared/model/product/product.model';
import React from 'react';
import './ProductOptions.scss';

interface ProductOptionsProps {
  data: IAttribute[];
}

const ProductOptions: React.FC<ProductOptionsProps> = ({ data }) => {
  return (
    <>
      <dl className='row small mb-3'>
        <dt className='col-sm-3'>Availability</dt>
        {/* <dd className='col-sm-9'>{info.isOnSale ?? 'In stock'}</dd> */}
        <dt className='col-sm-3'>Sold by</dt>
        <dd className='col-sm-9'>Authorised Store</dd>
      </dl>

      <div className='product-attribute'>
        <span className='attribute-label'>Attribute:</span>
        <div className='attribute-options'>
          {data &&
            data?.map(attribute => (
              <span key={attribute.id} className='attribute-option'>
                {attribute.attribute}
              </span>
            ))}
        </div>
      </div>
      <dd className='col-sm-9'>{/* Size options */}</dd>
      <dt className='col-sm-3'>Size</dt>
      <dd className='col-sm-9'>{/* Color options */}</dd>
    </>
  );
};

export default ProductOptions;
