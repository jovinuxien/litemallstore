import { IGood } from 'app/shared/model/product/product.model';
import React from 'react';
import { Link } from 'react-router-dom';

type CardProductGridProps = {
  data: {
    img: string;
    name: string;
    link: string;
    price: number;
    originPrice: number;
    star: number;
    isNew: boolean;
    isHot: boolean;
    discountPercentage: number;
    discountPrice: number;
  };
};

type CardProductGridProps1 = {
  data: IGood;
};

/**
 * A card component that displays a product in a grid layout.
 * @param {{ img: string, name: string, link: string, price: number, originPrice: number, star: number, isNew: boolean, isHot: boolean, discountPercentage: number, discountPrice: number }} data
 * @returns {JSX.Element}
 * @constructor
 */
const CardProductGrid = ({ data }: CardProductGridProps1): JSX.Element => {
  const product = data;
  return (
    <div className='card'>
      {/* Product image */}
      <img src={product.picUrl} className='card-img-top' alt='...' />
      {/* If the product is new, display a "New" badge */}
      {product.isNew && <span className='badge bg-success position-absolute mt-2 ms-2'>New</span>}
      {/* If the product is hot, display a "Hot" badge */}
      {product.isHot && <span className='badge bg-danger position-absolute r-0 mt-2 me-2'>Hot</span>}
      {/* If the product has a discount, display a badge with the discount percentage or price */}
      {(product.counterPrice > 0 || product.counterPrice > 0) && (
        <span className={`rounded position-absolute p-2 bg-warning  ms-2 small ${product.isNew ? 'mt-5' : 'mt-2'}`}>
          {/*           -{product.discountPercentage > 0 ? product.discountPercentage + '%' : '$' + product.discountPrice}
           */}{' '}
          {product.counterPrice > 0 ? product.counterPrice + '%' : '$' + product.counterPrice}
        </span>
      )}
      <div className='card-body'>
        {/* Product name */}
        <h6 className='card-subtitle mb-2'>
          <Link to={`/product/${product.id}`} className='text-decoration-none'>
            {product.name}
          </Link>
        </h6>
        <div className='my-2'>
          {/* Product price */}
          <span className='fw-bold h5'>${product.retailPrice}</span>
          {/* If the product has an origin price, display it with a strike-through */}
          {product.counterPrice > 0 && <del className='small text-muted ms-2'>${product.retailPrice}</del>}
          {/* Product rating */}
          <span className='ms-2'>
            {Array.from({ length: product.counterPrice }, (_, key) => (
              <i className='bi bi-star-fill text-warning me-1' key={key} />
            ))}
          </span>
        </div>
        <div className='btn-group  d-flex' role='group'>
          {/* Add to cart button */}
          <button type='button' className='btn btn-sm btn-primary' title='Add to cart'>
            <i className='bi bi-cart-plus' />
          </button>
          {/* Add to wishlist button */}
          <button type='button' className='btn btn-sm btn-outline-secondary' title='Add to wishlist'>
            <i className='bi bi-heart-fill' />
          </button>
        </div>
      </div>
    </div>
  );
};

export default CardProductGrid;
