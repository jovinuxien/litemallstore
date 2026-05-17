import { IGood } from 'app/shared/model/product/product.model';
import React from 'react';
import { Link } from 'react-router-dom';

export type Product = {
  img: string;
  name: string;
  description: string;
  link: string;
  price: number;
  originPrice: number;
  star: number;
  isNew: boolean;
  isHot: boolean;
  isFreeShipping: boolean;
  discountPercentage: number;
  discountPrice: number;
};
type CardProductListProps = {
  data: Product;
};

type CardProductListProps1 = {
  data: IGood;
};

const CardProductList: React.FC<CardProductListProps1> = props => {
  const product = props.data;
  return (
    <div className='card'>
      <div className='row g-0'>
        <div className='col-md-3 text-center'>
          <img src={product.picUrl} className='img-fluid' alt='...' />
        </div>
        <div className='col-md-6'>
          <div className='card-body'>
            <h6 className='card-subtitle me-2 d-inline'>
              <Link to={`/product/${product.id}`} className='text-decoration-none'>
                {product.name}
              </Link>
            </h6>
            {product.isNew && <span className='badge bg-success me-2'>New</span>}
            {product.isHot && <span className='badge bg-danger me-2'>Hot</span>}

            <div>
              {product.counterPrice > 0 &&
                Array.from({ length: 5 }, (_, key) => {
                  if (key <= product.counterPrice) return <i className='bi bi-star-fill text-warning me-1' key={key} />;
                  else return <i className='bi bi-star-fill text-secondary me-1' key={key} />;
                })}
            </div>
            {product.brief && product.brief.includes('|') === false && <p className='small mt-2'>{product.brief}</p>}
            {product.brief && product.brief.includes('|') && (
              <ul className='mt-2'>
                {product.brief.split('|').map((desc, idx) => (
                  <li key={idx}>{desc}</li>
                ))}
              </ul>
            )}
          </div>
        </div>
        <div className='col-md-3'>
          <div className='card-body'>
            <div className='mb-2'>
              <span className='fw-bold h5'>${product.counterPrice}</span>
              {product.counterPrice > 0 && <del className='small text-muted ms-2'>${product.counterPrice}</del>}
              {(product.counterPrice > 0 || product.counterPrice > 0) && (
                <span className={`rounded p-1 bg-warning ms-2 small`}>
                  -{product.counterPrice > 0 ? product.counterPrice + '%' : '$' + product.counterPrice}
                </span>
              )}
            </div>
            {product.isFreeShipping && (
              <p className='text-success small mb-2'>
                <i className='bi bi-truck' /> Free shipping
              </p>
            )}

            <div className='btn-group d-flex' role='group'>
              <button type='button' className='btn btn-sm btn-primary' title='Add to cart'>
                <i className='bi bi-cart-plus' />
              </button>
              <button type='button' className='btn btn-sm btn-outline-secondary' title='Add to wishlist'>
                <i className='bi bi-heart-fill' />
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default CardProductList;
