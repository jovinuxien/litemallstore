import React from 'react';
import { Link } from 'react-router-dom';

type ItemProps = {
  to: string;
  img: string;
  title: string;
  description: string;
  index?: number;
};

type BannerProps = {
  id: string;
  className: string;
  data: ItemProps[];
};
const Item: React.FC<ItemProps> = itemProps => (
  <div className={`carousel-item ${itemProps.index === 0 ? 'active' : ''}`}>
    <Link to={itemProps.to}>
      <img src={itemProps.img} className='img-fluid' alt={itemProps.description} />
      {(itemProps.title || itemProps.description) && (
        <div className='carousel-caption d-none d-md-block'>
          {itemProps.title && <h5>{itemProps.title}</h5>}
          {itemProps.description && <p>{itemProps.description}</p>}
        </div>
      )}
    </Link>
  </div>
);
export const Banner1: React.FC<BannerProps> = props => {
  return (
    <div id='myCarousel' className='carousel slide' data-bs-ride='carousel'>
      <div className='carousel-inner'>
        <div className='carousel-item active'>
          <img className='d-block w-90' src={props.data[0].img} alt='First slide' />
        </div>
        <div className='carousel-item'>
          <img className='d-block w-90' src={props.data[0].img} alt='Second slide' />
        </div>
        <div className='carousel-item'>
          <img className='d-block w-90' src={props.data[0].img} alt='Third slide' />
        </div>
      </div>

      <button className='carousel-control-prev' data-bs-slide='prev' data-bs-target='#myCarousel'>
        <span className='carousel-control-prev-icon' aria-hidden='true'></span>
        <span className='visually-hidden'>Previous</span>
      </button>
      <button className='carousel-control-next' data-bs-slide='next' data-bs-target='#myCarousel'>
        <span className='carousel-control-next-icon' aria-hidden='true'></span>
        <span className='visually-hidden'>Next</span>
      </button>
    </div>
  );
};

export default Banner1;
