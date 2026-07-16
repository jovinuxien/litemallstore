import { AUTHORITIES } from 'app/config/constants';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginAdmin, loginAffiliate } from 'app/shared/reducers/admin-auth';
import IconShieldLock from 'bootstrap-icons/icons/shield-lock.svg';
import Umbrella from 'bootstrap-icons/icons/umbrella.svg';
import React, { useEffect, useState } from 'react';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';

export interface Credentials {
  username: string;
  password: string;
}

type LoginTab = 'admin' | 'affiliate';

// Two realms on one form (Wave 5): the Admin tab logs litemall_admin accounts
// into /admin, the Affiliate tab logs litemall_user promoters into the
// /affiliate portal. The post-login destination is keyed off the ROLE in the
// store (not the tab), so a stale deep-link `from` can never land a principal
// on the other realm's routes — PrivateRoute would redirect anyway (edge
// SecurityConfig is the real boundary).
const SignInForm: React.FC = () => {
  const { isAuthenticated, loading, errorMessage, authorities } = useAppSelector(state => state.adminAuth);
  const dispatch = useAppDispatch();
  const navigateTo = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname;
  const [tab, setTab] = useState<LoginTab>('admin');

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Credentials>({
    defaultValues: { username: '', password: '' },
  });

  useEffect(() => {
    if (isAuthenticated) {
      if (authorities.includes(AUTHORITIES.AFFILIATE)) {
        navigateTo(from && from.startsWith('/affiliate') ? from : '/affiliate/dashboard', { replace: true });
      } else {
        navigateTo(from && !from.startsWith('/affiliate') ? from : '/admin', { replace: true });
      }
    }
  }, [isAuthenticated, authorities]);

  const onSubmit: SubmitHandler<Credentials> = async data => {
    await dispatch(tab === 'affiliate' ? loginAffiliate(data) : loginAdmin(data));
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
      <ul className='nav nav-tabs mb-3'>
        <li className='nav-item'>
          <button
            type='button'
            className={`nav-link ${tab === 'admin' ? 'active' : ''}`}
            onClick={() => setTab('admin')}
          >
            Admin
          </button>
        </li>
        <li className='nav-item'>
          <button
            type='button'
            className={`nav-link ${tab === 'affiliate' ? 'active' : ''}`}
            onClick={() => setTab('affiliate')}
          >
            Affiliate
          </button>
        </li>
      </ul>

      {tab === 'affiliate' && (
        <div className='form-text text-muted mb-2'>
          Sign in with your shop account. Affiliate access is granted by an administrator.
        </div>
      )}

      {errorMessage && <div className='alert alert-danger'>{errorMessage}</div>}

      <div className={`form-group ${errors.username ? 'is-invalid' : ''}`}>
        <label htmlFor='username'>
          <Umbrella /> Your UserName
        </label>
        <Controller
          name='username'
          control={control}
          rules={{ required: 'Username is required' }}
          render={({ field }) => <input id='username' className='form-control mb-3' {...field} type='text' />}
        />
        {errors.username && <div className='invalid-feedback'>{errors.username.message}</div>}
      </div>

      <div className={`form-group ${errors.password ? 'is-invalid' : ''}`}>
        <label htmlFor='password'>
          <IconShieldLock /> Your password
        </label>
        <Controller
          name='password'
          control={control}
          rules={{ required: 'Password is required' }}
          render={({ field }) => <input id='password' className='form-control mb-3' {...field} type='password' />}
        />
        {errors.password && <div className='invalid-feedback'>{errors.password.message}</div>}
      </div>

      <div className='d-grid'>
        <button type='submit' className='btn btn-primary mb-3' disabled={loading}>
          {loading ? 'Logging in…' : tab === 'affiliate' ? 'Log in to affiliate portal' : 'Log In'}
        </button>
      </div>

      <Link className='float-start' to='/account/signup' title='Sign Up'>
        Create your account
      </Link>
      <Link className='float-end' to='/account/forgotpassword' title='Forgot Password'>
        Forgot password?
      </Link>
      <div className='clearfix'></div>
    </form>
  );
};

export default SignInForm;
