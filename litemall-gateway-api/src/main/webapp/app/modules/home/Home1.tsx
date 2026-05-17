import React from 'react';
import { Button, Card, Carousel, Col, Container, Row } from 'react-bootstrap';

type Product = {
  id: number;
  title: string;
  description: string;
  image: string;
};

type Banners = {
  id: number;
  title: string;
  description: string;
  image: string;
  link: string;
};

const banners: Banners[] = [
  { id: 1, title: 'Hot Deals', description: 'Get amazing discounts now!', image: 'https://via.placeholder.com/1920x400', link: '#' },
  { id: 2, title: 'New Deals', description: 'Get amazing discounts now!', image: 'https://via.placeholder.com/1920x400', link: '#' },
  { id: 3, title: 'Hot Deals', description: 'Get amazing discounts now!', image: 'https://via.placeholder.com/1920x400', link: '#' },
];

const products: Product[] = [
  { id: 1, title: 'Product 1', description: 'Description 1', image: 'https://via.placeholder.com/800x300' },
  { id: 2, title: 'Product 2', description: 'Description 2', image: 'https://via.placeholder.com/800x300' },
  { id: 3, title: 'Product 3', description: 'Description 3', image: 'https://via.placeholder.com/800x300' },
  { id: 4, title: 'Product 4', description: 'Description 4', image: 'https://via.placeholder.com/800x300' },
  { id: 5, title: 'Product 5', description: 'Description 5', image: 'https://via.placeholder.com/800x300' },
  { id: 6, title: 'Product 6', description: 'Description 6', image: 'https://via.placeholder.com/800x300' },
  { id: 7, title: 'Product 7', description: 'Description 7', image: 'https://via.placeholder.com/800x300' },
  { id: 8, title: 'Product 8', description: 'Description 8', image: 'https://via.placeholder.com/800x300' },
];

const chunkArray = <T,>(arr: T[], chunkSize: number): T[][] => {
  return Array.from({ length: Math.ceil(arr.length / chunkSize) }, (_, i) => arr.slice(i * chunkSize, i * chunkSize + chunkSize));
};

const Home1Page: React.FC = () => {
  const [activeIndex, setActiveIndex] = React.useState(0);

  const productChunks = chunkArray(products, 4);
  const bannerChunks = chunkArray(banners, 1);
  const handleSelect = (selectedIndex: number) => {
    setActiveIndex(selectedIndex);
  };
  return (
    <Container>
      <Row className='mb-3'>
        <Col>
          <Carousel>
            {bannerChunks.map((chunk, idx) => (
              // eslint-disable-next-line react/jsx-key
              <Carousel.Item key={idx}>
                <img className='d-block w-100' src={chunk[0].image} alt='First slide' />
                <Carousel.Caption>
                  <h3>{chunk[0].title}</h3>
                  <p>{chunk[0].description}</p>
                </Carousel.Caption>
              </Carousel.Item>
            ))}
          </Carousel>
        </Col>
      </Row>
      <Row className='mt-4'>
        <h3>Product Carousel</h3>
        <Carousel activeIndex={activeIndex} onSelect={handleSelect}>
          {productChunks.map((chunk, idx) => (
            <Carousel.Item key={idx}>
              <Row>
                {chunk.map(product => (
                  // eslint-disable-next-line react/jsx-key
                  <div key={product.id} className='col-md-3'>
                    <Card>
                      <Card.Img variant='top' src={product.image} />
                      <Card.Body>
                        <Card.Title>{product.title}</Card.Title>
                        <Card.Text>{product.description}</Card.Text>
                        <Button>viewe Details</Button>
                      </Card.Body>
                    </Card>
                  </div>
                ))}
              </Row>
            </Carousel.Item>
          ))}
        </Carousel>
      </Row>

      <Row>
        <Col>
          <h3>Categories</h3>
        </Col>
      </Row>
      <Row>
        {[...Array(4)].map((_, idx) => (
          <Col md={3} key={idx}>
            <Card className='mb-4'>
              <Card.Img variant='top' src='https://via.placeholder.com/150' />
              <Card.Body>
                <Card.Title>Category {idx + 1}</Card.Title>
                <Card.Text>Explore more in this category.</Card.Text>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>

      <Row>
        <Col>
          <h3>Top Products</h3>
        </Col>
      </Row>
      <Row>
        {[...Array(8)].map((_, idx) => (
          <Col md={3} key={idx}>
            <Card className='mb-4'>
              <Card.Img variant='top' src='https://via.placeholder.com/150' />
              <Card.Body>
                <Card.Title>Product {idx + 1}</Card.Title>
                <Card.Text>
                  $19.99 <span className='text-muted'>$29.99</span>
                </Card.Text>
                <button className='btn btn-primary'>Add to Cart</button>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>
    </Container>
  );
};

export default Home1Page;
