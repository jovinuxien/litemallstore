import React from 'react';

type Meta = {
  touched: boolean;
  error?: string;
  warning?: string;
};

type SelectProps = React.SelectHTMLAttributes<HTMLSelectElement>;

interface renderFormSelectProps extends SelectProps {
  input: SelectProps;
  label: string;
  tips?: string;
  options: { value: string; label: string }[];
  required?: boolean;
  meta: Meta;
}

const renderSelectOptions = (item: { value: string; label: string }) => (
  <option key={item.value} value={item.value}>
    {item.label}
  </option>
);
//export default function renderFormSelect({
const renderFormSelect: React.FC<renderFormSelectProps> = props => {
  const {
    input,
    label,
    tips,
    options,
    required,
    meta: { touched, error, warning },
  } = props;
  return (
    <React.Fragment>
      <label className={`form-label ${required ? 'required' : ''}`} htmlFor={input.name}>
        {label}
      </label>
      <select {...input} {...props} id={input.name} className='form-select'>
        {options?.map(renderSelectOptions)}
      </select>
      {tips && <div className='form-text'>{tips}</div>}
      {touched && ((error && <div className='invalid-feedback'>{error}</div>) || (warning && <span>{warning}</span>))}
    </React.Fragment>
  );
};
export default renderFormSelect;
