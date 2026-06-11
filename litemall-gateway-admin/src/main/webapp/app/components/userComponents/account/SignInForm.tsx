import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginAdmin } from 'app/shared/reducers/admin-auth';
import IconShieldLock from 'bootstrap-icons/icons/shield-lock.svg';
import Umbrella from 'bootstrap-icons/icons/umbrella.svg';
import React, { useEffect } from 'react';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { Link, useLocation, useNavigate } from 'react-router-dom';

export interface Credentials {
  username: string;
  password: string;
}

const SignInForm: React.FC = () => {
  const { isAuthenticated, loading, errorMessage } = useAppSelector(state => state.adminAuth);
  const dispatch = useAppDispatch();
  const navigateTo = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: { pathname?: string } })?.from?.pathname || '/admin';

  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Credentials>({
    defaultValues: { username: '', password: '' },
  });

  useEffect(() => {
    if (isAuthenticated) {
      navigateTo(from, { replace: true });
    }
  }, [isAuthenticated]);

  const onSubmit: SubmitHandler<Credentials> = async data => {
    await dispatch(loginAdmin(data));
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)}>
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
          {loading ? 'Logging in…' : 'Log In'}
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
