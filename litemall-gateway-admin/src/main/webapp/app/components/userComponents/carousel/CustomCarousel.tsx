import React from 'react';
import { Carousel } from 'react-bootstrap';

type CustomCarouselProps = {
  id?: string;
  children: React.ReactNode;
  className?: string;
};

type CustomItemCarouselProps = {
  children?: React.ReactNode;
};

export const CustomCarouselItem: React.FC<CustomItemCarouselProps> = ({ children }) => {
  return <Carousel.Item>{children}</Carousel.Item>;
};
export const CustomCarousel: React.FC<CustomCarouselProps> = ({ id, children }) => {
  return <Carousel>{children}</Carousel>;
};

//export default CustomCarousel;
