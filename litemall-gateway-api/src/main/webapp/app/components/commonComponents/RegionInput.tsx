import React, { useMemo, useState } from 'react';
import { Form } from 'react-bootstrap';

/**
 * Free-text input with a static suggestion dropdown (regions/states/provinces).
 * Focus shows the country's full list; typing narrows it; picking fills the
 * field — but any typed value stays valid, so countries without a dataset (or
 * regions outside it) degrade to a plain input. No external calls.
 */
interface Props {
  value: string;
  onChange: (text: string) => void;
  suggestions: string[];
  placeholder?: string;
  required?: boolean;
  isInvalid?: boolean;
  name?: string;
}

const fold = (s: string) => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');

const RegionInput: React.FC<Props> = ({ value, onChange, suggestions, placeholder, required, isInvalid, name }) => {
  const [open, setOpen] = useState(false);

  const filtered = useMemo(() => {
    const q = fold(value.trim());
    if (!q) return suggestions;
    const matches = suggestions.filter(s => fold(s).includes(q));
    // An exact pick leaves nothing to suggest — keep the dropdown quiet.
    return matches.length === 1 && fold(matches[0]) === q ? [] : matches;
  }, [suggestions, value]);

  const showList = open && filtered.length > 0;

  return (
    <div className='position-relative'>
      <Form.Control
        name={name}
        value={value}
        onChange={e => {
          onChange(e.target.value);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => window.setTimeout(() => setOpen(false), 200)}
        placeholder={placeholder}
        required={required}
        isInvalid={isInvalid}
        autoComplete='address-level1'
      />
      {showList && (
        <div
          className='position-absolute bg-white border rounded shadow-sm mt-1 w-100'
          style={{ zIndex: 1060, maxHeight: 240, overflowY: 'auto' }}
          role='listbox'
        >
          {filtered.map(s => (
            <button
              key={s}
              type='button'
              role='option'
              aria-selected={s === value}
              className={`dropdown-item small py-2 ${s === value ? 'active' : ''}`}
              onMouseDown={e => e.preventDefault()}
              onClick={() => {
                onChange(s);
                setOpen(false);
              }}
            >
              {s}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default RegionInput;
