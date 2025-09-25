import { faBell, faHeart, faShoppingCart, faUser } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { logoutThunk } from 'app/shared/reducers/authSlice';

import { getUserInfo } from 'app/shared/reducers/profileSlice';
import React, { lazy, useEffect, useState } from 'react';
import { Badge, Button, Col, Container, Dropdown, FormControl, Nav, Navbar, Row } from 'react-bootstrap';
import { Form, Link, useNavigate } from 'react-router-dom';
const Search = lazy(() => import('./Search'));

import { BASE_URL_CONTEXT } from 'app/config/api';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { CategoryData } from 'app/shared/model/category/category.models';
import axios from 'axios';
import './Header.scss';

interface AuthenticateProps {
  children: React.ReactNode;
}
type TopMenuProps = {
  categoryListMenu: CategoryData[];
  currentCategory: CategoryData | null;
  currentSubCategories: CategoryData[] | null;
};

interface ChildrenCategory {
  id: number;
  name: string;
  iconUrl: string;
}

interface ChildrenSubCategory {
  id: number;
  name: string;
  children: ChildrenSubCategory[];
  iconUrl: string;
}

export const Authed: React.FC<AuthenticateProps> = ({ children }) => {
  return <>{children}</>;
};
export const UnAuthed: React.FC<AuthenticateProps> = ({ children }) => {
  return <>{children}</>;
};
const Header: React.FC<TopMenuProps> = ({ categoryListMenu, currentCategory, currentSubCategories }) => {
  const dispatch = useAppDispatch();
  const { isAuthenticated } = useAppSelector(state => state.auth.data);
  const profileState = useAppSelector(state => state.profile.data);
  const [searchQuery, setSearchQuery] = React.useState('');
  const [subCategories, setSubCategories] = useState({});

  const [newCategoryListMenu, setNewCategoryListMenu] = React.useState<CategoryData[]>([]);

  const navigate = useNavigate();

  const { cartList } = useAppSelector(state => state.cart.data);
  const handleLogout = async () => {
    try {
      await dispatch(logoutThunk());
      //console.log(`The result is: ${result}`);
      navigate('/');
    } catch (error) {
      console.error('Logout Error, and the reason is:', error);
      /* alert(`Logout failed: ${error}`); */
    }
  };

  const fetchSubCategories = async categoryId => {
    try {
      const response = await axios.get(`${BASE_URL_CONTEXT}/catalog/current?id=${categoryId}`);
      setSubCategories(prevState => ({
        ...prevState,
        [categoryId]: response.data.data.currentSubCategory,
      }));
    } catch (error) {
      console.error('Error fetching subcategories:', error);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    // Implement search functionality
    console.log('Searching for:', searchQuery);
  };

  const handleUserProfileInfo = () => {
    if (isAuthenticated) {
      dispatch(getUserInfo());
      navigate('/account/profile');
    }
  };

  const getNewCategoryListMenu = (): CategoryData[] => {
    /* return categoryListMenu.map(category => {
      const children: CategoryData[] =
        currentSubCategories
          ?.filter(subCategory => subCategory.pid === category.id)
          .map(subcategory => ({
            ...subcategory,
          })) || [];

      return {
        ...category,
        children,
      };
    }); */
    return categoryListMenu.map(category => {
      const children: CategoryData[] =
        currentSubCategories
          ?.filter(subCategory => subCategory.pid === category.id)
          .map(subCategory => ({
            ...subCategory,
            parentCategory: {
              id: category.id,
              name: category.name,
            },
          })) || [];

      return {
        ...category,
        children,
      };
    });
  };
  useEffect(() => {
    if (isAuthenticated) {
      dispatch(getUserInfo());
    }
    setNewCategoryListMenu(getNewCategoryListMenu());
  }, [categoryListMenu, currentSubCategories]);
  return (
    <header className='groupon-header'>
      <Navbar bg='light' expand='lg' className='py-2 category-nav'>
        <Container>
          <Navbar.Brand as={Link} to='/'>
            <img src='/content/images/logo.png' alt='Groupon' height='30' />
          </Navbar.Brand>
          <Navbar.Toggle aria-controls='basic-navbar-nav' />
          <Navbar.Collapse id='basic-navbar-nav'>
            <Row className='w-100 align-items-center'>
              <Col lg={8} md={6} className='my-2 my-lg-0'>
                <Form className='d-flex w-100' onSubmit={handleSearch}>
                  <FormControl
                    type='search'
                    placeholder='Search Groupon'
                    className='me-2 flex-grow-1'
                    aria-label='Search'
                    value={searchQuery}
                    onChange={e => setSearchQuery(e.target.value)}
                  />
                  <Button variant='outline-success' type='submit'>
                    Search
                  </Button>
                </Form>
              </Col>
              <Col lg={2} md={3} className='my-0 my-lg-0'></Col>

              <Nav className='ml-auto'>
                {isAuthenticated ? (
                  <>
                    <Nav.Link as={Link} to='account/wishlist' className='d-flex align-items-center'>
                      <FontAwesomeIcon icon={faHeart} />
                      <Badge bg='success' className='ml-1'>
                        5
                      </Badge>
                    </Nav.Link>
                    <Nav.Link as={Link} to='/notifications' className='d-flex align-items-center'>
                      <FontAwesomeIcon icon={faBell} />
                      <Badge bg='success' className='ml-1'>
                        2
                      </Badge>
                    </Nav.Link>
                    <Nav.Link as={Link} to='/cart' className='d-flex align-items-center'>
                      <FontAwesomeIcon icon={faShoppingCart} />
                      <Badge bg='success' className='ml-1'>
                        {cartList.length}
                      </Badge>
                    </Nav.Link>
                  </>
                ) : (
                  <>
                    <Nav.Link as={Link} to='/cart' className='d-flex align-items-center'>
                      <FontAwesomeIcon icon={faShoppingCart} />
                      <Badge bg='success' className='ml-1'>
                        {cartList.length}
                      </Badge>
                    </Nav.Link>
                  </>
                )}

                <Dropdown>
                  <Dropdown.Toggle as={Nav.Link} id='dropdown-user'>
                    <FontAwesomeIcon icon={faUser} />
                  </Dropdown.Toggle>

                  <Dropdown.Menu align='end'>
                    {isAuthenticated ? (
                      <>
                        <Dropdown.Item as={Link} to='/account'>
                          Profile
                        </Dropdown.Item>
                        <Dropdown.Item as={Link} to='/account/wishlist'>
                          Wishlist
                        </Dropdown.Item>
                        <Dropdown.Item as={Link} to='/account/orders'>
                          Orders
                        </Dropdown.Item>
                        <Dropdown.Divider />
                        <Dropdown.Item onClick={handleLogout}>Logout</Dropdown.Item>
                      </>
                    ) : (
                      <>
                        <Dropdown.Item as={Link} to='account/signin'>
                          {/* <FontAwesomeIcon icon={faSignInAlt} className='me-2' /> */}
                          Login
                        </Dropdown.Item>
                        <Dropdown.Item as={Link} to='account/signup'>
                          {/* <FontAwesomeIcon icon={faUserPlus} className='me-2' /> */}
                          Sign Up
                        </Dropdown.Item>
                      </>
                    )}
                  </Dropdown.Menu>
                </Dropdown>
                {/*  <Nav.Link as={Link} to='/account'>
                <FontAwesomeIcon icon={faUser} />
              </Nav.Link> */}
              </Nav>
            </Row>
          </Navbar.Collapse>
        </Container>
      </Navbar>
      <Navbar bg='white' className='py-2 category-nav'>
        <Container>
          <Row className='w-100 align-items-center'>
            <Col xs='auto'>
              <Nav>
                <li className='nav-item dropdown'>
                  <button className='btn nav-link dropdown-toggle fw-bold hamburger-menu' id='navbarDropdown' data-bs-toggle='dropdown' aria-expanded='false'>
                    <span className='hamburger-icon' />
                  </button>
                  <ul className='dropdown-menu' aria-labelledby='navbarDropdown'>
                    <li>
                      <Link className='dropdown-item' to='/account/orders'>
                        Categories
                      </Link>
                    </li>
                    <li>
                      <hr className='dropdown-divider' />
                    </li>
                    <li>
                      <Link className='dropdown-item' to='/blog'>
                        Blog
                      </Link>
                    </li>
                    <li>
                      <Link className='dropdown-item' to='/blog/detail'>
                        Blog Detail
                      </Link>
                    </li>
                    <li>
                      <hr className='dropdown-divider' />
                    </li>
                    <li>
                      <Link className='dropdown-item' to='/fsafasf'>
                        404 Page Not Found
                      </Link>
                    </li>
                    <li>
                      <Link className='dropdown-item' to='/500'>
                        500 Internal Server Error
                      </Link>
                    </li>
                  </ul>
                </li>
              </Nav>
            </Col>
          </Row>

          <Row>
            <Col>
              {/*  <Nav className='mx-auto'>
                {categoryListMenu.slice(0, 5).map(category => (
                  <Dropdown
                    key={category.id}
                    onToggle={isOpen => {
                      if (isOpen && !subCategories[category.id]) {
                        fetchSubCategories(category.id);
                      }
                    }}
                  >
                    <Dropdown.Toggle as={Nav.Link} id={`dropdown-${category.id}`}>
                      <img src={category.iconUrl} alt={category.name} height='30' />
                      {category.name}
                    </Dropdown.Toggle>
                    <Dropdown.Menu>
                      {subCategories[category.id] ? (
                        subCategories[category.id].map(subCategory => (
                          <Dropdown.Item key={subCategory.id} as={Link} to={`/category/${category.id}/${subCategory.id}`}>
                            {subCategory.name || 'Unnamed Subcategory'}
                          </Dropdown.Item>
                        ))
                      ) : (
                        <Dropdown.Item>Loading subcategories...</Dropdown.Item>
                      )}
                    </Dropdown.Menu>
                  </Dropdown>
                ))}
              </Nav> */}
              <Nav className='category-menu'>
                {categoryListMenu.slice(0, 6).map(category => (
                  <Nav.Item key={category.id} className='category-item'>
                    <Nav.Link as={Link} to={`/category/${category.id}`} onMouseEnter={() => fetchSubCategories(category.id)}>
                      <img src={category.iconUrl} alt={category.name} height='30' />
                      {category.name}
                    </Nav.Link>
                    {subCategories[category.id] && (
                      <div className='subcategory-menu'>
                        <div className='subcategory-list'>
                          {subCategories[category.id].map(subCategory => (
                            <Link
                              key={subCategory.id}
                              to={`/category/${category.id}/${subCategory.id}`}
                              className='subcategory-item'
                              style={{ fontFamily: 'inherit', fontSize: 'inherit', fontWeight: 'inherit', font: 'bold' }}
                            >
                              {subCategory.name}
                            </Link>
                          ))}
                        </div>
                      </div>
                    )}
                  </Nav.Item>
                ))}
              </Nav>
            </Col>
          </Row>
        </Container>
      </Navbar>
    </header>
  );
};

export default Header;
