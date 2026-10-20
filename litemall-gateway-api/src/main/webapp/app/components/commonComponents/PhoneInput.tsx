import { useTranslation } from 'app/i18n';
import { useLocale } from 'app/i18n/locale';
import React, { useEffect, useMemo, useRef, useState } from 'react';

import {
  DialCountry,
  checkPhoneLength,
  dialCountries,
  localeGuess,
  splitInternational,
  toE164,
} from 'app/shared/data/dialCodes';
import { PhoneCountrySource } from 'app/shared/util/deliverySync';

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
 *
 * Two-way country sync. A form that also has a Country field passes it as
 * `countryIso2`: when that country changes, the dial code follows and the digits
 * are kept. The one exception is a number the customer typed in full international
 * form — that is their number as they wrote it, and a later country change must not
 * silently re-prefix it. In the other direction `onCountryChange` reports every
 * change with WHY it happened (see {@link PhoneCountrySource}), so the form can
 * follow an explicit pick without being dragged around by a start-up default.
 */
interface Props {
  value?: string;
  onChange: (e164: string) => void;
  /**
   * Fires with the selected ISO2 on mount and on every change, with the reason:
   * `init` (start-up default that something named — a stored number, the form's
   * country, the site language or the browser's locale), `fallback` (start-up default
   * that nothing named: the selector shows +1 but no country should be inferred),
   * `pick` (chosen in the selector), `typed` (derived from a typed international
   * number) or `external` (followed `countryIso2`).
   */
  onCountryChange?: (iso2: string, source: PhoneCountrySource) => void;
  /**
   * The form's own country. The dial code follows it whenever it changes; a stored
   * number keeps its own country at mount, and a number typed in full international
   * form is never re-prefixed.
   */
  countryIso2?: string;
  /**
   * The countries the form's own Country field offers (ISO2). Only a dial code from this
   * list can move that field — so they are listed FIRST in the selector, and a pick
   * outside it says why the country did not follow instead of silently doing nothing.
   * Omit on forms with no country field (registration).
   */
  deliveryCountries?: string[];
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
  countryIso2,
  deliveryCountries,
  defaultIso2,
  isInvalid,
  placeholder,
}) => {
  const { t } = useTranslation();
  const { lang: siteLang } = useLocale();
  const [initial] = useState(() => parseInitial(value));
  // The start-up country and what it is worth. Something the form or a stored number
  // supplied is a fact (`init`, no remark). Otherwise the visitor's locale is asked:
  // a confident answer (site language, browser region/language) is a guess the form
  // may act on (`init`) and that earns the "we do not deliver there" note when it
  // falls outside the delivery list — a blank Country must be explained. The bare
  // fallback is reported as such (`fallback`) and never fills anything.
  const [start] = useState<{ iso2: string; source: PhoneCountrySource; guessed: boolean }>(() => {
    const known = (code?: string) => (code && dialCountries().some(c => c.iso2 === code) ? code : undefined);
    const supplied = initial.iso2 ?? known(countryIso2) ?? known(defaultIso2);
    if (supplied) return { iso2: supplied, source: 'init', guessed: false };
    const guess = localeGuess({ siteLang });
    return { iso2: guess.iso2, source: guess.confident ? 'init' : 'fallback', guessed: guess.confident };
  });
  const [iso2, setIso2] = useState<string>(start.iso2);
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

  // Why the country last changed — read by the single emit below.
  const sourceRef = useRef<PhoneCountrySource>(start.source);
  // True while the number on screen is a FULL international number: typed that way
  // (also after blur folded it into selector + national digits) or loaded from a stored
  // E.164 value. Such a number is not re-prefixed when the form's country changes —
  // "+46 70…" under a German address is a Swedish mobile, not a typo. A selector pick
  // or clearing the field resets it.
  const typedIntlRef = useRef(!!initial.iso2);
  // The customer chose this phone country themselves (selector pick or a number typed in
  // full) — as opposed to a start-up default or a follow of the form's country. Only an
  // explicit choice earns the "we do not deliver there" note below.
  const [explicit, setExplicit] = useState(false);

  const countryChangeRef = useRef(onCountryChange);
  countryChangeRef.current = onCountryChange;
  useEffect(() => {
    countryChangeRef.current?.(effectiveIso2, sourceRef.current); // mount + every change — one code path
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

  // Follow the form's country. Runs on CHANGES of the prop only — at mount a stored
  // number's own country wins (see the initial state above).
  const followedRef = useRef(countryIso2);
  useEffect(() => {
    if (countryIso2 === followedRef.current) return;
    followedRef.current = countryIso2;
    const target = countries.find(c => c.iso2 === countryIso2);
    if (!target || target.iso2 === effectiveIso2) return;
    const hasDigits = effectiveNational.replace(/\D/g, '').length > 0;
    if (typedIntlRef.current && hasDigits) return; // a number typed in full stays as typed
    sourceRef.current = 'external';
    typedIntlRef.current = false;
    setExplicit(false);
    setIso2(target.iso2);
    if (intl) setNational(intl.national); // drop a bare "+49" so text and selector agree
    emit(target.dial, effectiveNational);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [countryIso2]);

  const handleText = (raw: string) => {
    setNational(raw);
    sourceRef.current = 'typed';
    const split = splitInternational(raw, iso2);
    if (split) {
      typedIntlRef.current = true;
      setExplicit(true);
    } else if (!raw.trim()) typedIntlRef.current = false;
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
    sourceRef.current = 'pick';
    typedIntlRef.current = false;
    setExplicit(true);
    setIso2(c.iso2);
    if (intl) setNational(nat); // explicit pick wins over stale "+…" text
    setOpen(false);
    setFilter('');
    emit(c.dial, nat);
  };

  // With a filter typed, one flat list of matches. Without one, the countries the form can
  // deliver to come first under their own heading: they are the ones that move the form's
  // Country field, and burying 8 of them among ~200 made the pairing look broken.
  const delivers = (iso: string) => !!deliveryCountries?.includes(iso);
  const grouped = !!deliveryCountries?.length && !filter.trim();
  const sections: { heading: string | null; items: DialCountry[] }[] = grouped
    ? [
        { heading: t('common:forms.phone.deliverTo'), items: countries.filter(c => delivers(c.iso2)) },
        { heading: t('common:forms.phone.otherCountries'), items: countries.filter(c => !delivers(c.iso2)) },
      ]
    : [{ heading: null, items: filtered }];

  // The customer picked (or typed) a number from a country the form cannot deliver to.
  // That is a perfectly good phone number — but the Country field did not follow it, and
  // saying nothing made the two fields look out of step.
  // A confident locale guess the customer has not touched yet counts too, while the
  // form still has no country: a Finnish browser opens on +358 with Country blank, and
  // without the note that blank looks like a bug.
  const formCountryName = countries.find(c => c.iso2 === countryIso2)?.name;
  const standingGuess = start.guessed && !countryIso2 && effectiveIso2 === start.iso2;
  const outsideDelivery = (explicit || standingGuess) && !!deliveryCountries?.length && !delivers(effectiveIso2);
  const deliveryNote = !outsideDelivery
    ? null
    : formCountryName
      ? t('common:forms.phone.notDelivered', { phoneCountry: selected.name, country: formCountryName })
      : t('common:forms.phone.notDeliveredChoose', { phoneCountry: selected.name });

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
          autoComplete='tel'
          className={`form-control${isInvalid || showError ? ' is-invalid' : ''}`}
          value={national}
          placeholder={placeholder ?? t('common:forms.phone.placeholder')}
          onChange={e => handleText(e.target.value)}
          onBlur={handleBlur}
        />
      </div>
      {showError && lengthMessage && <div className='invalid-feedback d-block'>{lengthMessage}</div>}
      {deliveryNote && (
        <div className='form-text' data-delivery-note>
          <i className='bi bi-info-circle me-1' />
          {deliveryNote}
        </div>
      )}
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
          {sections.map(section => (
            <React.Fragment key={section.heading ?? 'matches'}>
              {section.heading && section.items.length > 0 && (
                <div className='px-3 pt-2 pb-1 small fw-semibold text-muted' role='presentation'>
                  {section.heading}
                </div>
              )}
              {section.items.map(c => (
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
            </React.Fragment>
          ))}
          {filtered.length === 0 && <div className='text-muted small p-2'>{t('common:forms.phone.noMatch')}</div>}
        </div>
      )}
    </div>
  );
};

export default PhoneInput;
