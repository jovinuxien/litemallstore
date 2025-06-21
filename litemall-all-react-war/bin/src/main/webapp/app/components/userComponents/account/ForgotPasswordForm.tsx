import { useAppDispatch } from 'app/config/hooks';
import RenderFormGroupField from 'app/helpers/renderFormGroupField';
import React from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
// Validation helpers
const validateRequired = (value: string) => (value ? undefined : 'This field is required');
const validateMaxLength = (max: number) => (value: string) => (value.length <= max ? undefined : `Maximum length is ${max}`);
const validateMinLength = (min: number) => (value: string) => (value.length >= min ? undefined : `Minimum length is ${min}`);
const validateDigits = (value: string) => (/^\d+$/.test(value) ? undefined : 'Only digits are allowed');

interface ForgotPasswordFormFields {
  mobileNo: string;
}

interface ForgotPasswordFormProps {
  onSubmit: (data: ForgotPasswordFormFields) => void;
}
const ForgotPasswordForm: React.FC<ForgotPasswordFormProps> = ({ onSubmit }) => {
  const dispatch = useAppDispatch();

  const {
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordFormFields>();

  //const onSubmit = (data: ForgotPasswordFormFields) => {};

  return (
    <form onSubmit={handleSubmit(onSubmit)} className='needs-validation' noValidate>
      <Controller
        name='mobileNo'
        control={control}
        defaultValue=''
        rules={{
          required: 'This field is required',
          validate: {
            maxLength: validateMaxLength(15),
            minLength: validateMinLength(4),
            digits: validateDigits,
          },
        }}
        render={({ field, fieldState }) => (
          <RenderFormGroupField
            input={undefined}
            Icon={undefined}
            meta={{
              touched: false,
              error: fieldState.error?.message,
              warning: '',
            }}
            {...field}
          />
        )}
      />
      <div className='d-grid'>
        <button type='submit' className='btn btn-primary mb-3' disabled={isSubmitting}>
          Submit
        </button>
      </div>
      <Link className='float-start' to='/account/signup' title='Sign Up'>
        Create your account
      </Link>
      <Link className='float-end' to='/account/signin' title='Sign In'>
        Sign In
      </Link>
    </form>
  );
};

export default ForgotPasswordForm;
