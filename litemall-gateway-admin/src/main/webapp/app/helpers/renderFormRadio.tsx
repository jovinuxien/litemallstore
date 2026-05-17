import React from 'react';

type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type InputProps = React.AllHTMLAttributes<HTMLInputElement>;

interface renderFormRadioProps extends InputProps {
  input: InputProps;
  label: string;
  id: string;
  meta: Meta;
}

const renderFormRadio: React.FC<renderFormRadioProps> = props => {
  const { input, label, id } = props;
  return (
    <div className='form-check form-check-inline'>
      <input {...input} {...props} checked={input.value === props.value} className='form-check-input' type='radio' />
      <label className='form-check-label' htmlFor={id}>
        {label}
      </label>
    </div>
  );
};
export default renderFormRadio;
