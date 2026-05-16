import { useAppDispatch, useAppSelector } from 'app/config/store';
import { loginAdminThunk, loginUserThunk } from 'app/shared/reducers/authSlice';
import IconShieldLock from 'bootstrap-icons/icons/shield-lock.svg';
import Umbrella from 'bootstrap-icons/icons/umbrella.svg';
import React, { useEffect } from 'react';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';

export interface Credentials {
  username: string;
  password: string;
}
interface SignInFormProps {
  //onSubmit: SubmitHandler<Credentials>;
}
const SignInForm: React.FC<SignInFormProps> = () => {
  const { token, isAuthenticated, isAuthenticatedAdmin, adminToken } = useAppSelector(state => state.auth.data);
  const dispatch = useAppDispatch();
  const navigateTo = useNavigate();
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Credentials>({
    defaultValues: {
      username: '',
      password: '',
    },
  });

  const onSubmit: SubmitHandler<Credentials> = async data => {
    const existingAdminToken = sessionStorage.getItem('adminToken');
    const existingUserToken = sessionStorage.getItem('token');

    if (existingUserToken) {
      navigateTo('/');
      return;
    }
    if (existingAdminToken) {
      navigateTo('/private/dashboard');
      return;
    }

    try {
      const adminAuth = await dispatch(loginAdminThunk(data)).unwrap();
      //const adminAuth = await dispatch(loginUserThunk(data)).unwrap();
      const resultAuth = await dispatch(loginUserThunk(data)).unwrap();
      console.log('Authentication result:', resultAuth);

      if (adminAuth.data?.token) {
        if (adminAuth.data.token) {
          sessionStorage.setItem('adminToken', adminAuth.data.token);
          navigateTo('/private/dashboard');
        } else {
          console.error('Admin Authentication failed:', adminAuth.errmsg);
          sessionStorage.setItem('token', resultAuth.data.token);
          navigateTo('/');
        }
      } else if (resultAuth.data?.token) {
        sessionStorage.setItem('token', resultAuth.data.token);
        navigateTo('/');
      } else {
        console.error(' Common Authentication failed:', resultAuth.errmsg);
      }
    } catch (error) {
      console.error('Authentication error:', error);
    }
  };

  useEffect(() => {}, []);

  return (
    //<form onSubmit={handleSubmit(onSubmit)} className={`needs-validation ${submitFailed ? 'was-validated' : ''}`} noValidate>
    <form onSubmit={handleSubmit(onSubmit)}>
      {/* Mobile Number Field */}
      <div className={`form-group ${errors.username ? 'is-invalid' : ''}`}>
        <label htmlFor='username'>
          <Umbrella /> Your UserName
        </label>

        <Controller
          name='username'
          control={control}
          render={({ field, fieldState }) => <input id='username' className='form-control mb-3' {...field} type='text' />}
          defaultValue=''
        />

        {errors.username && <div className='invalid-feedback'>{errors.username.message}</div>}
      </div>

      {/* Password Field */}
      <div className={`form-group ${errors.password ? 'is-invalid' : ''}`}>
        <label htmlFor='password'>
          <IconShieldLock /> Your password
        </label>
        <Controller
          name='password'
          control={control}
          render={({ field }) => <input id='password' className='form-control mb-3' {...field} type='password' />}
          defaultValue=''
        />
        {errors.password && <div className='invalid-feedback'>{errors.password.message}</div>}
      </div>

      {/* Submit Button */}
      <div className='d-grid'>
        {/*         <button type='submit' className='btn btn-primary mb-3' disabled={submitting}>
         */}{' '}
        <button type='submit' className='btn btn-primary mb-3'>
          Log In
        </button>
      </div>

      {/* Links */}
      <Link className='float-start' to='/account/signup' title='Sign Up'>
        Create your account
      </Link>
      <Link className='float-end' to='/account/forgotpassword' title='Forgot Password'>
        Forgot password?
      </Link>
      <div className='clearfix'></div>

      <hr />

      {/* Social Login */}
      <div className='row'>
        <div className='col text-center'>
          <p className='text-muted small'>Or you can join with</p>
        </div>
        <div className='col text-center'>
          <Link to='/' className='btn btn-light text-white bg-twitter me-3'>
            <i className='bi bi-twitter-x' />
          </Link>
          <Link to='/' className='btn btn-light text-white me-3 bg-facebook'>
            <i className='bi bi-facebook mx-1' />
          </Link>
          <Link to='/' className='btn btn-light text-white me-3 bg-google'>
            <i className='bi bi-google mx-1' />
          </Link>
        </div>
      </div>
    </form>
  );
};

export default SignInForm;
