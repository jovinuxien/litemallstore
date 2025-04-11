import React from 'react';

type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type InputProps = React.InputHTMLAttributes<HTMLInputElement>;

interface renderFormCheckboxProps extends InputProps {
  input: InputProps;
  label: string;
  id: string;
  meta: Meta;
}

const renderFormCheckbox: React.FC<renderFormCheckboxProps> = ({
  input,
  label,
  id,
  meta: { touched, error, warning }, // destructuring meta props,
  ...props // Collect additional props for the input
}) => {
  return (
    <div className='form-check form-check-inline'>
      <input {...input} {...props} className='form-check-input' id={id} type='checkbox' />
      <label className='form-check-label' htmlFor={id}>
        {label}
      </label>
      {touched && ((error && <div className='invalid-feedback'>{error}</div>) || (warning && <span>{warning}</span>))}
    </div>
  );
};

export default renderFormCheckbox;
