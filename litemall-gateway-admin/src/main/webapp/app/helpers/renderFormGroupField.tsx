import React from 'react';

type InputProps = React.InputHTMLAttributes<HTMLInputElement>;
type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type RenderFormInputProps = {
  input: InputProps;
  label?: string;
  tips?: string;
  Icon: React.ComponentType;
  required?: boolean;
  className?: string;
  meta: Meta;
  [key: string]: unknown;
};

const RenderFormGroupField: React.FC<RenderFormInputProps> = ({
  input,
  label,
  tips,
  Icon,
  required,
  className = '',
  meta: { touched, error, warning },
  ...props
}) => {
  return (
    <div className={`form-group ${className}`}>
      {label && (
        <label className={`form-label ${required ? 'required' : ''}`} htmlFor={input.name}>
          {label}
        </label>
      )}

      <div className='input-group'>
        <span className='input-group-text'>
          <Icon />
        </span>
        <input {...input} {...props} id={input.name} className='form-control' />
        {touched && ((error && <div className='invalid-feedback'>{error}</div>) || (warning && <span>{warning}</span>))}
      </div>
      {tips && <div className='form-text'>{tips}</div>}
    </div>
  );
};

export default RenderFormGroupField;
