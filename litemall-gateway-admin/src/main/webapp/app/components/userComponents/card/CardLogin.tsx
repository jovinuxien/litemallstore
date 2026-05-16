import React from 'react';
import { Link } from 'react-router-dom';

/* type CardLoginProps = {
  className?: '';
}; */

const CardLogin: React.FC = () => {
  /* return (
    <div className={`card shadow-sm ${props.className}`}>
      <div className='card-body text-center'>
        <h5 className='card-title'>Sign in for your best experience</h5>
        <Link to='account/signin' className='btn btn-warning'>
          Sign in securely
        </Link>
      </div>
    </div>
  ); */
  return (
    <div className='card shadow-sm mt-3'>
      <div className='card-body text-center'>
        <h5 className='card-title'>Sign in for your best experience</h5>
        <Link to='account/signin' className='btn btn-warning'>
          Sign in securely
        </Link>
      </div>
    </div>
  );
};

export default CardLogin;
