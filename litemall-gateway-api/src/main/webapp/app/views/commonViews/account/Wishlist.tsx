import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getUserInfo } from 'app/shared/reducers/profileSlice';
import React, { lazy, useEffect } from 'react';
import { Card, Col, Container, Row } from 'react-bootstrap';
import { data } from '../../../data';
const CardProductList2 = lazy(() => import('../../../components/userComponents/card/CardProductList2'));

const WishlistView = () => {
  const dispatch = useAppDispatch();
  const { isAuthenticated } = useAppSelector(state => state.auth.data);
  const profileState = useAppSelector(state => state.profile.data);

  useEffect(() => {
    dispatch(getUserInfo());
  }, [dispatch]);

  return (
    <Container className='py-5'>
      <Row className='justify-content-center'>
        <Col md={10} lg={8}>
          <Card>
            <Card.Body>
              <>
                <h4 className='my-3'>Wishlists</h4>
                <div className='row g-3'>
                  {data.products.map((product, idx) => {
                    return (
                      <div key={idx} className='col-md-6'>
                        <CardProductList2 data={product} />
                      </div>
                    );
                  })}
                </div>
              </>
            </Card.Body>
          </Card>
        </Col>
      </Row>
    </Container>
  );
};

export default WishlistView;
