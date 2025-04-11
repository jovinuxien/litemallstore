import * as React from 'react';
import { Button, Card, CardBody, CardFooter, Col, Container, InputGroup, Row } from 'react-bootstrap';
import { Input } from 'reactstrap';

class Register extends React.Component {
  render() {
    return (
      <div className='app flex-row align-items-center'>
        <Container>
          <Row className='justify-content-center'>
            <Col md='6'>
              <Card className='mx-4'>
                <CardBody className='p-4'>
                  <h1>Register</h1>
                  <p className='text-muted'>Create your account</p>
                  <InputGroup className='mb-3'>
                    <InputGroup.Text>
                      <i className='icon-user' />
                    </InputGroup.Text>
                    <Input type='text' placeholder='Username' />
                  </InputGroup>
                  <InputGroup className='mb-3'>
                    <InputGroup.Text>@</InputGroup.Text>
                    <Input type='text' placeholder='Email' />
                  </InputGroup>
                  <InputGroup className='mb-3'>
                    <InputGroup.Text>
                      <i className='icon-lock' />
                    </InputGroup.Text>
                    <Input type='password' placeholder='Password' />
                  </InputGroup>
                  <InputGroup className='mb-4'>
                    <InputGroup.Text>
                      <i className='icon-lock' />
                    </InputGroup.Text>
                    <Input type='password' placeholder='Repeat password' />
                  </InputGroup>
                  <Button variant='success' className='w-100'>
                    Create Account
                  </Button>
                </CardBody>
                <CardFooter className='p-4'>
                  <Row>
                    <Col xs='12' sm='6'>
                      <Button variant='primary' className='btn-facebook w-100'>
                        <span>facebook</span>
                      </Button>
                    </Col>
                    <Col xs='12' sm='6'>
                      <Button variant='info' className='btn-twitter w-100'>
                        <span>twitter</span>
                      </Button>
                    </Col>
                  </Row>
                </CardFooter>
              </Card>
            </Col>
          </Row>
        </Container>
      </div>
    );
  }
}

export default Register;
