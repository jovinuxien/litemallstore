import { useAppDispatch, useAppSelector } from 'app/config/store';
import { CategoryData } from 'app/shared/model/category/category.models';
import React, { useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import './TopMenu.scss';

type TopMenuProps = {
  categoryListMenu: CategoryData[];
};

const TopMenu: React.FC<TopMenuProps> = ({ categoryListMenu }) => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { isAuthenticated } = useAppSelector(state => state.auth.data);

  const handleClick = (categoryId: number) => {
    navigate(`/categories/${categoryId}`);
  };

  useEffect(() => {
    console.log(isAuthenticated, '' + sessionStorage.getItem('token'));
  }, []);

  return (
    <nav className='navbar navbar-expand-lg navbar-light bg-light  p-0 m-1'>
      <div className='container-fluid'>
        <Link className='navbar-brand' to='/'>
          JoMall
        </Link>
        <button
          className='navbar-toggler'
          type='button'
          data-bs-toggle='collapse'
          data-bs-target='#navbarSupportedContent'
          aria-controls='navbarSupportedContent'
          aria-expanded='false'
          aria-label='Toggle navigation'
        >
          <span className='navbar-toggler-icon' />
        </button>
        <div className='collapse navbar-collapse' id='navbarSupportedContent'>
          <ul className='navbar-nav'>
            <li className='nav-item dropdown'>
              <button
                className='btn nav-link dropdown-toggle fw-bold hamburger-menu'
                id='navbarDropdown'
                data-toggle='dropdown'
                aria-expanded='false'
                data-bs-toggle='dropdown'
              >
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
            {categoryListMenu.map((category, idx) => (
              <li key={idx} className='nav-item'>
                {/* <Link to={`/category/${category.id}`} className='nav-link'>
                  <div className='text-center'>
                    <h4>{category.name}</h4>
                  </div>
                </Link> */}
                <button
                  className='nav-link'
                  onClick={() => handleClick(category.id)}
                  style={{ background: 'none', border: 'none', color: 'pink', cursor: 'pointer' }}
                >
                  {category.name}
                </button>
              </li>
            ))}
          </ul>
          {/* {JSON.stringify(categoryListMenu)} */}
        </div>
      </div>
    </nav>
  );
};

export default TopMenu;
