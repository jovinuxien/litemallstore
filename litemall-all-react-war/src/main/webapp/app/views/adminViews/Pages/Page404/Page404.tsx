import * as React from 'react';
import { Button, Col, Container, Input, InputGroup, InputGroupText, Row } from 'reactstrap';

class Page404 extends React.Component {
  render() {
    return (
      <div className='app flex-row align-items-center'>
        <Container>
          <Row className='justify-content-center'>
            <Col md='6'>
              <div className='clearfix'>
                <h1 className='float-left display-3 mr-4'>404</h1>
                <h4 className='pt-3'>Oops! You are lost.</h4>
                <p className='text-muted float-left'>The page you are looking for was not found.</p>
              </div>
              <InputGroup>
                <InputGroupText>
                  <i className='fa fa-search' />
                </InputGroupText>
                <Input size={16} type='text' placeholder='What are you looking for?' />
                <Button color='info'>Search</Button>
              </InputGroup>
            </Col>
          </Row>
        </Container>
      </div>
    );
  }
}

export default Page404;
