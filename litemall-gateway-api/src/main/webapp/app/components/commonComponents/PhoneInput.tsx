import React, { useEffect, useMemo, useRef, useState } from 'react';

import { DialCountry, dialCountries, toE164 } from 'app/shared/data/dialCodes';

/**
 * Phone input with a searchable country dial-code selector (Wave 16).
 * Emits the composed E.164 value (`+<dial><digits>`) — the store keeps ONE
 * canonical phone shape for orders and CJ. Static dataset, no external calls.
 */
interface Props {
  value?: string;
  onChange: (e164: string) => void;
  /** Fires with the selected ISO2 on mount and on every country pick, so callers can follow the phone country. */
  onCountryChange?: (iso2: string) => void;
  defaultIso2?: string;
  isInvalid?: boolean;
  placeholder?: string;
}

const guessIso2 = (): string => {
  const region = (navigator.language.split('-')[1] ?? '').toUpperCase();
  return dialCountries().some(c => c.iso2 === region) ? region : 'US';
};

// Split an existing stored value back into country + national digits so the
// component can EDIT a pre-filled phone, not just capture a fresh one. E.164
// values match the longest dial prefix; legacy non-"+" values keep their digits
// under the guessed country (retyping normalizes them). Mount-time only — pass
// a `key` to re-seed when the logical record behind the field changes.
const parseInitial = (value: string | undefined): { iso2?: string; national: string } => {
  const v = (value ?? '').trim();
  if (!v.startsWith('+')) return { national: v };
  const digits = v.slice(1);
  const matches = dialCountries().filter(c => digits.startsWith(c.dial));
  if (!matches.length) return { national: digits };
  const longest = Math.max(...matches.map(c => c.dial.length));
  const candidates = matches.filter(c => c.dial.length === longest);
  // Shared dials (+1 US/CA, +7 RU/KZ, …) are genuinely ambiguous — break the
  // tie with the browser locale's country so a US visitor sees 🇺🇸, not the
  // dataset's first +1 entry.
  const best = candidates.find(c => c.iso2 === guessIso2()) ?? candidates[0];
  return { iso2: best.iso2, national: digits.slice(best.dial.length) };
};

const PhoneInput: React.FC<Props> = ({ value, onChange, onCountryChange, defaultIso2, isInvalid, placeholder }) => {
  const [initial] = useState(() => parseInitial(value));
  const [iso2, setIso2] = useState<string>(() => initial.iso2 ?? defaultIso2 ?? guessIso2());
  const [national, setNational] = useState(initial.national);
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const wrapRef = useRef<HTMLDivElement>(null);

  const countryChangeRef = useRef(onCountryChange);
  countryChangeRef.current = onCountryChange;
  useEffect(() => {
    countryChangeRef.current?.(iso2); // mount + every pick — one code path
  }, [iso2]);

  const countries = dialCountries();
  const selected = countries.find(c => c.iso2 === iso2) ?? countries[0];
  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return countries;
    return countries.filter(c => c.name.toLowerCase().includes(q) || c.dial.startsWith(q.replace('+', '')) || c.iso2.toLowerCase() === q);
  }, [countries, filter]);

  const emit = (dial: string, nat: string) => onChange(toE164(dial, nat));

  const pick = (c: DialCountry) => {
    setIso2(c.iso2);
    setOpen(false);
    setFilter('');
    emit(c.dial, national);
  };

  return (
    <div className='position-relative' ref={wrapRef}>
      <div className='input-group'>
        <button
          type='button'
          className='btn btn-outline-secondary d-flex align-items-center gap-1'
          aria-label='Select country code'
          aria-expanded={open}
          onClick={() => setOpen(o => !o)}
        >
          <span aria-hidden>{selected.flag}</span>
          <span className='small'>+{selected.dial}</span>
          <i className='bi bi-chevron-down small' />
        </button>
        <input
          type='tel'
          className={`form-control${isInvalid ? ' is-invalid' : ''}`}
          value={national}
          placeholder={placeholder ?? 'Phone number'}
          onChange={e => {
            setNational(e.target.value);
            emit(selected.dial, e.target.value);
          }}
        />
      </div>
      {open && (
        <div
          className='position-absolute bg-white border rounded shadow-sm mt-1 w-100'
          style={{ zIndex: 1060, maxHeight: 260, overflowY: 'auto' }}
          role='listbox'
        >
          <div className='p-2 border-bottom bg-light sticky-top'>
            <input
              type='text'
              className='form-control form-control-sm'
              placeholder='Search country…'
              value={filter}
              onChange={e => setFilter(e.target.value)}
              autoFocus
            />
          </div>
          {filtered.map(c => (
            <button
              key={c.iso2}
              type='button'
              role='option'
              aria-selected={c.iso2 === iso2}
              className={`dropdown-item d-flex align-items-center gap-2 py-1 ${c.iso2 === iso2 ? 'active' : ''}`}
              onClick={() => pick(c)}
            >
              <span aria-hidden>{c.flag}</span>
              <span className='flex-grow-1 text-truncate small'>{c.name}</span>
              <span className='text-muted small'>+{c.dial}</span>
            </button>
          ))}
          {filtered.length === 0 && <div className='text-muted small p-2'>No match</div>}
        </div>
      )}
    </div>
  );
};

export default PhoneInput;
