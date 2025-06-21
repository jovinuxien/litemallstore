/* eslint-disable @typescript-eslint/no-explicit-any */
import { useAppDispatch, useAppSelector } from 'app/config/hooks';
import { getAdminGoodsDetailThunk } from 'app/shared/reducers/private/catalogMgn/adminGoodsDetailSlice';
import React from 'react';
import { Badge, Card, Col, Container, ListGroup, Row } from 'react-bootstrap';
import { useParams } from 'react-router-dom';

const GoodsDetails: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const dispatch = useAppDispatch();
  const { data, loading } = useAppSelector(state => state.private.adminGoodsDetail);
  const { goods, attributes, categoryIds, products, specificationList } = data;
  React.useEffect(() => {
    dispatch(getAdminGoodsDetailThunk(parseInt(id)));
  }, [dispatch, id]);

  return (
    <Container className='goods-detail'>
      <h1>{goods?.name}</h1>
      <Row>
        <Col md={6}>
          <Card>
            <Card.Img variant='top' src={goods?.picUrl} />
            <Card.Body>
              <Card.Title>Price: ${goods?.retailPrice}</Card.Title>
              <Card.Text>{goods?.brief}</Card.Text>
            </Card.Body>
          </Card>
        </Col>
        <Col md={6}>
          <h2>Specifications</h2>
          <ListGroup>
            {specificationList?.map((spec, index) => (
              <ListGroup.Item key={index}>
                {spec.name}: {spec.valueList.join(', ')}
              </ListGroup.Item>
            ))}
          </ListGroup>

          <h2 className='mt-4'>Attributes</h2>
          <ListGroup>
            {attributes.map((attr, index) => (
              <ListGroup.Item key={index}>
                {attr.attribute}: {attr.value}
              </ListGroup.Item>
            ))}
          </ListGroup>
        </Col>
      </Row>

      <h2 className='mt-4'>Products</h2>
      <Row>
        {products.map((product, index) => (
          <Col md={4} key={index}>
            <Card>
              <Card.Body>
                <Card.Title>{product?.specifications.join(', ')}</Card.Title>
                <Card.Text>Price: ${product?.price}</Card.Text>
                <Card.Text>Number: {product?.number}</Card.Text>
              </Card.Body>
            </Card>
          </Col>
        ))}
      </Row>

      <h2 className='mt-4'>Categories</h2>
      {categoryIds.map((categoryId, index) => (
        <Badge key={index} bg='primary' className='me-2'>
          Category ID: {categoryId}
        </Badge>
      ))}
    </Container>
  );
};

export default GoodsDetails;
