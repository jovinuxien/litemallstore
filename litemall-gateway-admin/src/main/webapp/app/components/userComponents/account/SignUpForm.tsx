import { useAppDispatch, useAppSelector } from 'app/config/store';
import { registerThunk } from 'app/shared/reducers/registerSlice';
import Envelope from 'bootstrap-icons/icons/envelope.svg';
import Lock from 'bootstrap-icons/icons/lock.svg';
import Phone from 'bootstrap-icons/icons/phone.svg';

import Umbrella from 'bootstrap-icons/icons/umbrella.svg';

import React, { useEffect } from 'react';
import { Controller, SubmitHandler, useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';

const required = (value: string) => (value ? undefined : 'This field is required');

const maxLength = (max: number) => (value: string) => (value && value.length > max ? `Must be ${max} characters or less` : undefined);

const minLength = (min: number) => (value: string) => (value && value.length < min ? `Must be ${min} characters or more` : undefined);

const isDigit = (value: string) => (value && !/^\d+$/.test(value) ? 'Must be a digit' : undefined);

interface FormValues {
  username: string;
  mobile: string;
  code: string;
  password: string;
  passwordConfirm: string;
}
interface SignUpFormProps {
  /* onSubmit: (data: FormValues) => void; */
}

const SignUpForm: React.FC<SignUpFormProps> = () => {
  const { loading, errorMessage } = useAppSelector(state => state.register);
  const dispatch = useAppDispatch();
  const navigateTo = useNavigate();

  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>();

  const onSubmit: SubmitHandler<FormValues> = data => {
    dispatch(registerThunk(data));
    navigateTo('/');
  };

  /* const onSubmit: SubmitHandler<FormValues> = data => {
    navigateTo('/');
  }; */

  useEffect(() => {}, []);

  return (
    <form onSubmit={handleSubmit(onSubmit)} className={`needs-validation ${errors ? 'was-validated' : ''}`} noValidate>
      <div className='row mb-3'>
        <div className='col-md-6'>
          <div className={`form-group ${errors.username ? 'is-invalid' : ''}`}>
            <label htmlFor='firstname'>
              <Umbrella /> Your first name
            </label>

            <Controller
              name='username'
              control={control}
              render={({ field, fieldState }) => <input id='username' className='form-control mb-3' {...field} type='text' />}
              defaultValue=''
            />

            {errors.username && <div className='invalid-feedback'>{errors.username.message}</div>}
          </div>
        </div>
        <div className='col-md-6'>
          <div className={`form-group ${errors.mobile ? 'is-invalid' : ''}`}>
            <label htmlFor='mobile'>
              <Umbrella /> Mobile
            </label>

            <Controller
              name='mobile'
              control={control}
              render={({ field, fieldState }) => <input id='mobile' className='form-control mb-3' {...field} type='text' />}
              defaultValue=''
            />

            {errors.mobile && <div className='invalid-feedback'>{errors.mobile.message}</div>}
          </div>
        </div>
      </div>
      <div className='row mb-3'>
        <div className='col-md-6'>
          <div className={`form-group ${errors.code ? 'is-invalid' : ''}`}>
            <label htmlFor='email'>
              <Envelope /> Code
            </label>
            <Controller
              name='code'
              control={control}
              rules={{
                required: 'Email is required',
                pattern: {
                  value: /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i,
                  message: 'Invalid email address',
                },
              }}
              render={({ field }) => <input id='email' className={`form-control mb-3 ${errors.code ? 'is-invalid' : ''}`} {...field} type='email' />}
              defaultValue=''
            />
            {errors.code && <div className='invalid-feedback'>{errors.code.message}</div>}
          </div>
        </div>
        <div className='col-md-6'>
          <div className={`form-group ${errors.mobile ? 'is-invalid' : ''}`}>
            <label htmlFor='phoneNumber'>
              <Phone /> Your Phone Number
            </label>
            <Controller
              name='mobile'
              control={control}
              rules={{
                required: 'Phone number is required',
                pattern: {
                  value: /^[0-9]{10}$/,
                  message: 'Invalid phone number (10 digits required)',
                },
              }}
              render={({ field }) => <input id='phoneNumber' className={`form-control mb-3 ${errors.mobile ? 'is-invalid' : ''}`} {...field} type='tel' />}
              defaultValue=''
            />
            {errors.mobile && <div className='invalid-feedback'>{errors.mobile.message}</div>}
          </div>
        </div>
      </div>

      <div className={`form-group ${errors.password ? 'is-invalid' : ''}`}>
        <label htmlFor='password'>
          <Lock /> Your Password
        </label>
        <Controller
          name='password'
          control={control}
          rules={{
            required: 'Password is required',
            /* pattern: {
              value: /w+/,
              message: 'Password must contain at least 8 characters, one uppercase, one lowercase, one number and one special character',
            }, */
          }}
          render={({ field }) => <input id='password' className={`form-control mb-3 ${errors.password ? 'is-invalid' : ''}`} {...field} type='password' />}
          defaultValue=''
        />
        {errors.password && <div className='invalid-feedback'>{errors.password.message}</div>}
      </div>

      <div className={`form-group ${errors.passwordConfirm ? 'is-invalid' : ''}`}>
        <label htmlFor='passwordConfirm'>
          <Lock /> Password Confirmation
        </label>
        <Controller
          name='passwordConfirm'
          control={control}
          rules={{
            required: 'Password is required',
            /* pattern: {
              value: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,}$/,
              message: 'Password must contain at least 8 characters, one uppercase, one lowercase, one number and one special character',
            }, */
          }}
          render={({ field }) => (
            <input id='passwordConfirm' className={`form-control mb-3 ${errors.passwordConfirm ? 'is-invalid' : ''}`} {...field} type='password' />
          )}
          defaultValue=''
        />
        {errors.password && <div className='invalid-feedback'>{errors.passwordConfirm.message}</div>}
      </div>

      {/* <Controller
        name='password'
        control={control}
        defaultValue=''
        rules={{
          validate: value => required(value) || minLength(8)(value) || maxLength(20)(value),
        }}
        render={({ field }) => (
          <div className='form-group'>
            <label>Password Confirmation</label>
            <div className='input-group'>
              <span className='input-group-text'>
                <IconShieldLock />
              </span>
              <input {...field} type='password' placeholder='******' className={`form-control ${errors.password ? 'is-invalid' : ''}`} />
            </div>
            {errors.password && <div className='invalid-feedback'>{errors.password.message}</div>}
          </div>
        )}
      /> */}

      <div className='d-grid'>
        <button type='submit' className='btn btn-primary mb-3' disabled={isSubmitting}>
          Create
        </button>
      </div>

      <Link className='float-start' to='/account/signin' title='Sign In'>
        Sign In
      </Link>
      <Link className='float-end' to='/account/forgotpassword' title='Forgot Password'>
        Forgot password?
      </Link>

      <div className='clearfix'></div>
      <hr />
      <div className='row'>
        <div className='col- text-center'>
          <p className='text-muted small'>Or you can join with</p>
        </div>
        <div className='col- text-center'>
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

export default SignUpForm;
