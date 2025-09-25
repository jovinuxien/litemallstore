import { useAppDispatch } from 'app/config/store';
import IconShieldLock from 'bootstrap-icons/icons/shield-lock.svg';
import React from 'react';
import { useForm } from 'react-hook-form';

interface ChangePasswordFormValues {
  currentPassword: string;
  password: string;
  confirmPassword: string;
}

interface ChangePasswordFormProps {
  onSubmit: (data: ChangePasswordFormValues) => void;
}
const ChangePasswordForm: React.FC<ChangePasswordFormProps> = ({ onSubmit }) => {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isSubmitSuccessful },
  } = useForm<ChangePasswordFormValues>();

  const dispatch = useAppDispatch();
  //const submitFailed = useAppSelector(state => state.auth.submitFailed); // Assuming Redux slice tracks this

  // Validation functions (can also use `yup` or `zod` schemas for validation)
  //const validatePasswordMatch = (value: string) => value === watch('password') || 'Passwords do not match';

  // Submit handler
  /*  const onSubmit: SubmitHandler<ChangePasswordFormValues> = data => {
    dispatch(changePassword(data));
  }; */

  return (
    <div className='card border-info'>
      <h6 className='card-header bg-info text-white'>
        <i className='bi bi-key'></i> Change Password
      </h6>
      <div className='card-body'>
        <form onSubmit={handleSubmit(onSubmit)} className='' noValidate>
          {/* Current Password Field */}
          <div className='mb-3'>
            <label htmlFor='currentPassword' className='form-label'>
              Current Password
            </label>
            <div className='input-group'>
              <span className='input-group-text'>
                <IconShieldLock />
              </span>
              <input
                id='currentPassword'
                type='password'
                className=''
                placeholder='******'
                {...register('currentPassword', { required: 'Current password is required', minLength: 8, maxLength: 20 })}
              />
              {errors.currentPassword && <div className='invalid-feedback'>{errors.currentPassword.message}</div>}
            </div>
          </div>

          {/* New Password Field */}
          <div className='mb-3'>
            <label htmlFor='password' className='form-label'>
              New Password
            </label>
            <div className='input-group'>
              <span className='input-group-text'>
                <IconShieldLock />
              </span>
              <input
                id='password'
                type='password'
                className='for'
                placeholder='******'
                {...register('password', { required: 'New password is required', minLength: 8, maxLength: 20 })}
              />
              {errors.password && <div className='invalid-feedback'>{errors.password.message}</div>}
            </div>
          </div>

          {/* Confirm New Password Field */}
          <div className='mb-3'>
            <label htmlFor='confirmPassword' className='form-label'>
              Confirm New Password
            </label>
            <div className='input-group'>
              <span className='input-group-text'>
                <IconShieldLock />
              </span>
              <input
                id='confirmPassword'
                type='password'
                className=''
                placeholder='******'
                // {...register('confirmPassword', { validate: validatePasswordMatch })}
                {...register('confirmPassword', { required: 'Confirm password is required', minLength: 8, maxLength: 20 })}
              />
              {errors.confirmPassword && <div className='invalid-feedback'>{errors.confirmPassword.message}</div>}
            </div>
          </div>

          {/* Submit Button */}
          <button type='submit' className='btn btn-info d-flex' disabled={isSubmitting}>
            {isSubmitting ? 'Submitting...' : 'Submit'}
          </button>
        </form>
      </div>
    </div>
  );
};
