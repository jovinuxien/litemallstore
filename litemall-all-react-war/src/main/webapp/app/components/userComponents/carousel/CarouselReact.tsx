import { IBanner } from 'app/shared/model/home.models';
import React from 'react';
import { Carousel } from 'react-bootstrap';

type ItemProps = {
  children: React.ReactNode;
};
export const CarouselReactItem: React.FC<ItemProps> = ({ children }) => {
  return <Carousel.Item>{children}</Carousel.Item>;
};

type CarouselReactProps = {
  data: IBanner[];
};

export const CarouselReact: React.FC<CarouselReactProps> = ({ data = [] }) => {
  return (
    <Carousel fade>
      {data.map((item, index) => (
        <Carousel.Item key={index}>
          <img className='d-block w-100' src={item.url} alt='First slide' />
          <Carousel.Caption>
            <h3>{item.name}</h3>
            <p>{item.content}</p>
          </Carousel.Caption>
        </Carousel.Item>
      ))}
    </Carousel>
  );
};

export default CarouselReact;
