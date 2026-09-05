import { useTranslation } from 'app/i18n';
import React, { useEffect, useMemo, useRef, useState } from 'react';

import {
  DialCountry,
  checkPhoneLength,
  dialCountries,
  localeIso2,
  splitInternational,
  toE164,
} from 'app/shared/data/dialCodes';

/**
 * Phone input with a searchable country dial-code selector (Wave 16).
 * Emits the composed E.164 value (`+<dial><digits>`) — the store keeps ONE
 * canonical phone shape for orders and CJ. Static dataset, no external calls.
 *
 * The text field accepts a full international number too: typing or pasting
 * `+49 170…` (or `0049…`) live-syncs the country selector to the dialled
 * country and, on blur, normalizes the field back to the national digits —
 * the two controls can never disagree. Digit counts are validated against the
 * selected country's numbering plan (blur-time message, live validity out).
 */
interface Props {
  value?: string;
  onChange: (e164: string) => void;
  /** Fires with the selected ISO2 on mount and on every country pick, so callers can follow the phone country. */
  onCountryChange?: (iso2: string) => void;
  /** Fires whenever the number's validity (per-country digit count) changes. Empty input counts as valid. */
  onValidityChange?: (ok: boolean) => void;
  defaultIso2?: string;
  isInvalid?: boolean;
  placeholder?: string;
}

// Split an existing stored value back into country + national digits so the
// component can EDIT a pre-filled phone, not just capture a fresh one. E.164
// values match the longest dial prefix; legacy non-"+" values keep their digits
// under the guessed country (retyping normalizes them). Mount-time only — pass
// a `key` to re-seed when the logical record behind the field changes.
const parseInitial = (value: string | undefined): { iso2?: string; national: string } => {
  const v = (value ?? '').trim();
  const intl = splitInternational(v);
  if (!intl) return { national: v.startsWith('+') ? v.slice(1) : v };
  return { iso2: intl.country.iso2, national: intl.national };
};

const PhoneInput: React.FC<Props> = ({
  value,
  onChange,
  onCountryChange,
  onValidityChange,
  defaultIso2,
  isInvalid,
  placeholder,
}) => {
  const { t } = useTranslation();
  const [initial] = useState(() => parseInitial(value));
  const [iso2, setIso2] = useState<string>(() => initial.iso2 ?? defaultIso2 ?? localeIso2());
  const [national, setNational] = useState(initial.national);
  const [touched, setTouched] = useState(false);
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const wrapRef = useRef<HTMLDivElement>(null);

  const countries = dialCountries();

  // International text ("+49…"/"0049…") overrides the picker while present: the
  // dialled country is derived from the digits, so selector and text agree.
  const intl = useMemo(() => splitInternational(national, iso2), [national, iso2]);
  const effectiveIso2 = intl?.country.iso2 ?? iso2;
  const selected = countries.find(c => c.iso2 === effectiveIso2) ?? countries[0];
  const effectiveNational = intl ? intl.national : national;

  const countryChangeRef = useRef(onCountryChange);
  countryChangeRef.current = onCountryChange;
  useEffect(() => {
    countryChangeRef.current?.(effectiveIso2); // mount + every pick/dial-sync — one code path
  }, [effectiveIso2]);

  // "+999…" that matches no dial is its own error; length errors come from the plan table.
  const unknownCode = !intl && national.trim().startsWith('+') && national.replace(/\D/g, '').length >= 1;
  const lengthCheck = checkPhoneLength(effectiveIso2, effectiveNational);
  const valid = !unknownCode && lengthCheck.ok;

  const validityRef = useRef(onValidityChange);
  validityRef.current = onValidityChange;
  useEffect(() => {
    validityRef.current?.(valid);
  }, [valid]);

  const lengthMessage = unknownCode
    ? t('common:forms.phone.unknownCode')
    : lengthCheck.ok
      ? null
      : t('common:forms.phone.lengthDetail', {
          kind: lengthCheck.kind === 'short' ? t('common:forms.phone.tooShort') : t('common:forms.phone.tooLong'),
          country: selected.name,
          dial: selected.dial,
          expected: lengthCheck.min === lengthCheck.max ? `${lengthCheck.min}` : `${lengthCheck.min}–${lengthCheck.max}`,
          entered: lengthCheck.count,
        });
  const showError = touched && !valid;

  const filtered = useMemo(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return countries;
    return countries.filter(c => c.name.toLowerCase().includes(q) || c.dial.startsWith(q.replace('+', '')) || c.iso2.toLowerCase() === q);
  }, [countries, filter]);

  const emit = (dial: string, nat: string) => onChange(toE164(dial, nat));

  const handleText = (raw: string) => {
    setNational(raw);
    const split = splitInternational(raw, iso2);
    if (split) {
      emit(split.country.dial, split.national);
    } else if (raw.trim().startsWith('+') || raw.trim().startsWith('00')) {
      // International prefix but no dial matched (yet): pass the raw digits
      // through untouched — never silently double-prefix the picker's dial.
      const digits = raw.replace(/\D/g, '').replace(/^00/, '');
      onChange(digits ? `+${digits}` : '');
    } else {
      emit(selected.dial, raw);
    }
  };

  // Leaving the field folds "+49 170…" into selector=DE + national digits.
  const handleBlur = () => {
    setTouched(true);
    if (intl) {
      setIso2(intl.country.iso2);
      setNational(intl.national);
    }
  };

  const pick = (c: DialCountry) => {
    const nat = intl ? intl.national : national;
    setIso2(c.iso2);
    if (intl) setNational(nat); // explicit pick wins over stale "+…" text
    setOpen(false);
    setFilter('');
    emit(c.dial, nat);
  };

  return (
    <div className='position-relative' ref={wrapRef}>
      <div className='input-group'>
        <button
          type='button'
          className='btn btn-outline-secondary d-flex align-items-center gap-1'
          aria-label={t('common:forms.phone.selectCode')}
          aria-expanded={open}
          onClick={() => setOpen(o => !o)}
        >
          <span aria-hidden>{selected.flag}</span>
          <span className='small'>+{selected.dial}</span>
          <i className='bi bi-chevron-down small' />
        </button>
        <input
          type='tel'
          className={`form-control${isInvalid || showError ? ' is-invalid' : ''}`}
          value={national}
          placeholder={placeholder ?? t('common:forms.phone.placeholder')}
          onChange={e => handleText(e.target.value)}
          onBlur={handleBlur}
        />
      </div>
      {showError && lengthMessage && <div className='invalid-feedback d-block'>{lengthMessage}</div>}
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
              placeholder={t('common:forms.phone.search')}
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
              aria-selected={c.iso2 === effectiveIso2}
              className={`dropdown-item d-flex align-items-center gap-2 py-1 ${c.iso2 === effectiveIso2 ? 'active' : ''}`}
              onClick={() => pick(c)}
            >
              <span aria-hidden>{c.flag}</span>
              <span className='flex-grow-1 text-truncate small'>{c.name}</span>
              <span className='text-muted small'>+{c.dial}</span>
            </button>
          ))}
          {filtered.length === 0 && <div className='text-muted small p-2'>{t('common:forms.phone.noMatch')}</div>}
        </div>
      )}
    </div>
  );
};

export default PhoneInput;
