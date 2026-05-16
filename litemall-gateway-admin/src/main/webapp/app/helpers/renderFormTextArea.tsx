import React from 'react';

type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type InputTextAreaProps = React.InputHTMLAttributes<HTMLTextAreaElement>;

interface RenderTextAreaProps {
  input: InputTextAreaProps;
  label: string;
  placeholder?: string;
  tips?: string;
  required?: boolean;
  meta: Meta;
}
const renderFormTextArea: React.FC<RenderTextAreaProps> = props => {
  const {
    input,
    label,
    placeholder,
    tips,
    required,
    meta: { touched, error, warning },
  } = props;
  return (
    <React.Fragment>
      <label className={`form-label ${required ? 'required' : ''}`} htmlFor={input.name}>
        {label}
      </label>
      <textarea {...input} {...props} id={input.name} className='form-control' placeholder={placeholder} />
      {tips && <div className='form-text'>{tips}</div>}
      {touched && ((error && <div className='invalid-feedback'>{error}</div>) || (warning && <span>{warning}</span>))}
    </React.Fragment>
  );
};
export default renderFormTextArea;
