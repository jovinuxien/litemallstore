import { IBanner } from 'app/shared/model/home.models';
//import 'bootstrap/js/dist/carousel';
import 'bootstrap/dist/js/bootstrap.bundle.js';
import React from 'react';
import { Link } from 'react-router-dom';

type ItemProps = {
  id?: number;
  to?: string;
  img?: string;
  title?: string;
  description?: string;
  iBanner?: IBanner;
  index?: number;
};
type IndicatorProps = {
  item: string;
  index: number;
};

type BannerProps1 = {
  className: string;
  id: string;
  data: IBanner[];
};

type BannerProps = {
  className: string;
  id: string;
  data: ItemProps[];
};

const Item: React.FC<ItemProps> = itemProps => (
  <div className={`carousel-item  ${itemProps.index === 0 ? 'active' : ''}`}>
    <Link to={itemProps.to}>
      <img src={itemProps.img} className='img-fluid ' alt={itemProps.title} />
      {(itemProps.title || itemProps.description) && (
        <div className='carousel-caption d-none d-md-block'>
          {itemProps.title && <h5>{itemProps.title}</h5>}
          {itemProps.description && <p>{itemProps.description}</p>}
        </div>
      )}
    </Link>
  </div>
);

const Indicator: React.FC<IndicatorProps> = ({ item, index }) => (
  <li data-bs-target={`#${item}`} data-bs-slide-to={index} className={`${index === 0 ? 'active' : ''}`} />
);

const Banner: React.FC<BannerProps> = ({ className, id, data }) => {
  return (
    <div id={id} className={`carousel slide ${className}`} data-bs-ride='carousel' style={{ minHeight: 100 }}>
      <ol className='carousel-indicators'>
        {data.map((item, index) => (
          <Indicator item={id} index={index} key={index} />
        ))}
      </ol>
      <div className='carousel-inner'>
        {data.map((item, index) => (
          <Item {...item} index={index} key={index} />
        ))}
      </div>
      <a className='carousel-control-prev' href={`#${id}`} role='button' data-bs-slide='prev'>
        <span className='carousel-control-prev-icon' aria-hidden='true' />
        <span className='sr-only'>Previous</span>
      </a>
      <a className='carousel-control-next' href={`#${id}`} role='button' data-bs-slide='next'>
        <span className='carousel-control-next-icon' aria-hidden='true' />
        <span className='sr-only'>Next</span>
      </a>
    </div>
  );
};

export default Banner;
