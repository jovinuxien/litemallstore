import { faBell, faComments, faDollarSign, faEnvelope, faFile, faLock, faShield, faTasks, faUser, faWrench } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { useAppDispatch } from 'app/config/hooks';
import { logoutAdminThunk } from 'app/shared/reducers/authSlice';
import * as React from 'react';
import { Badge, Dropdown } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';

const HeaderDropdown: React.FC = () => {
  const [dropdownOpen, setDropdownOpen] = React.useState(false);

  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  const toggle = () => {
    setDropdownOpen(!dropdownOpen);
  };

  const handleLogout = async () => {
    try {
      await dispatch(logoutAdminThunk());
      //console.log(`The result is: ${result}`);
      navigate('/');
    } catch (error) {
      console.error('Logout Error, and the reason is:', error);
      /* alert(`Logout failed: ${error}`); */
    }
  };

  return (
    <Dropdown show={dropdownOpen} onToggle={toggle}>
      <Dropdown.Toggle as='span' id='dropdown-basic' className='nav-link'>
        <img src={'../../../img/admin/avatars/6.jpg'} className='img-avatar' alt='admin@bootstrapmaster.com' />
      </Dropdown.Toggle>

      <Dropdown.Menu align='end'>
        <Dropdown.Header className='text-center'>
          <strong>Account</strong>
        </Dropdown.Header>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faBell} /> Updates <Badge bg='info'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faEnvelope} /> Messages <Badge bg='success'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faTasks} /> Tasks <Badge bg='danger'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faComments} /> Comments <Badge bg='warning'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Header className='text-center'>
          <strong>Settings</strong>
        </Dropdown.Header>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faUser} /> Profile
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faWrench} /> Settings
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faDollarSign} /> Payments <Badge bg='secondary'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Item>
          <FontAwesomeIcon icon={faFile} /> Projects <Badge bg='primary'>42</Badge>
        </Dropdown.Item>
        <Dropdown.Divider />
        <Dropdown.Item>
          <FontAwesomeIcon icon={faShield} /> Lock Account
        </Dropdown.Item>
        <Dropdown.Item onClick={handleLogout}>
          <FontAwesomeIcon icon={faLock} /> Logout
        </Dropdown.Item>
      </Dropdown.Menu>
    </Dropdown>
  );
};

/*  render() {
    // const { ...attributes } = this.props;
    return this.dropAccnt();
  } */

export default HeaderDropdown;
