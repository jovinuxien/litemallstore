import React from 'react';

type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type InputProps = React.InputHTMLAttributes<HTMLInputElement>;

interface renderFormInputProps extends InputProps {
  input: InputProps;
  label: string;
  tips?: string;
  id: string;
  meta: Meta;
}

const renderFormField: React.FC<renderFormInputProps> = ({
  input,
  tips,
  label,
  required,
  meta: { touched, error, warning },
  // Additional props for the input here...
  ...props
}) => {
  return (
    <React.Fragment>
      {label && (
        <label className={`form-label ${required ? 'required' : ''}`} htmlFor={input.name}>
          {label}
        </label>
      )}
      <input {...input} {...props} id={input.name} className='form-control' />
      {tips && <div className='form-text'>{tips}</div>}
      {touched && ((error && <div className='invalid-feedback'>{error}</div>) || (warning && <span>{warning}</span>))}
    </React.Fragment>
  );
};
export default renderFormField;
