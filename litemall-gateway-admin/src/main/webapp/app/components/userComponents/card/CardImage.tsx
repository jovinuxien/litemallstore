import React from 'react';
import { Link } from 'react-router-dom';

type CardImageProps = {
  to: string;
  src: string;
  className?: string;
};
const CardImage: React.FC<CardImageProps> = props => {
  return (
    <Link to={props.to}>
      <div className={`card shadow-sm ${props.className}`}>
        <div className='card-body p-0'>
          <img src={props.src} className='img-fluid rounded' alt='...' />
        </div>
      </div>
    </Link>
  );
};

export default CardImage;
