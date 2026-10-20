import React, { useMemo, useState } from 'react';
import { Form } from 'react-bootstrap';

import { LabelledRegion } from 'app/shared/data/regions';

/**
 * Free-text input with a static suggestion dropdown (regions/states/provinces).
 * Focus shows the country's full list; typing narrows it; picking fills the
 * field — but any typed value stays valid, so countries without a dataset (or
 * regions outside it) degrade to a plain input. No external calls.
 *
 * While the form has no country yet, `labelled` offers the regions of EVERY
 * delivery country, each labelled with its country ("Skåne · Sweden"); a pick then
 * reports the country too through `onPick`, so the region can set the country
 * instead of being ignored. `suggestions` (the chosen country's own list) takes
 * precedence whenever it is non-empty.
 */
interface Props {
  value: string;
  onChange: (text: string) => void;
  suggestions: string[];
  /** Cross-country suggestions, used only while `suggestions` is empty. */
  labelled?: LabelledRegion[];
  /** A pick from the list: the region's own spelling and, from `labelled`, its country. Falls back to `onChange`. */
  onPick?: (region: string, country?: string) => void;
  placeholder?: string;
  required?: boolean;
  isInvalid?: boolean;
  name?: string;
}

interface Item {
  region: string;
  label: string;
  country?: string;
}

const fold = (s: string) => s.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');

const RegionInput: React.FC<Props> = ({ value, onChange, suggestions, labelled, onPick, placeholder, required, isInvalid, name }) => {
  const [open, setOpen] = useState(false);

  const items = useMemo<Item[]>(
    () => (suggestions.length > 0 || !labelled ? suggestions.map(s => ({ region: s, label: s })) : labelled.map(r => ({ region: r.region, label: r.label, country: r.country }))),
    [suggestions, labelled]
  );

  const filtered = useMemo(() => {
    const q = fold(value.trim());
    if (!q) return items;
    const matches = items.filter(s => fold(s.label).includes(q));
    // An exact pick leaves nothing to suggest — keep the dropdown quiet.
    return matches.length === 1 && fold(matches[0].region) === q ? [] : matches;
  }, [items, value]);

  const showList = open && filtered.length > 0;

  const pick = (item: Item) => {
    if (onPick) onPick(item.region, item.country);
    else onChange(item.region);
    setOpen(false);
  };

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
              key={s.country ? `${s.country}:${s.region}` : s.region}
              type='button'
              role='option'
              aria-selected={s.region === value}
              className={`dropdown-item small py-2 ${s.region === value ? 'active' : ''}`}
              onMouseDown={e => e.preventDefault()}
              onClick={() => pick(s)}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default RegionInput;
