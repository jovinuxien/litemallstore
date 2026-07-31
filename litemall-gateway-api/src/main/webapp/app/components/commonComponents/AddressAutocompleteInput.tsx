import React, { useEffect, useRef, useState } from 'react';
import { Form } from 'react-bootstrap';

import { loadSiteConfig } from 'app/shared/config/siteConfig';

/**
 * Address line input with env-gated Google Places suggestions (Wave 16).
 * `placesApiKey` null in /auth/site-config ⇒ this renders a PLAIN input and
 * makes zero external requests — byte-identical behavior to before. With a
 * key, keystrokes are debounced against the Places Autocomplete API (New,
 * CORS-enabled REST — no SDK script), and picking a suggestion resolves the
 * place's components so the caller can fill city/region/zip alongside the
 * street line. Any API failure degrades silently back to typing.
 */

export interface ResolvedAddress {
  line: string;
  city?: string;
  region?: string;
  postalCode?: string;
  countryCode?: string;
}

interface Suggestion {
  placeId: string;
  text: string;
}

interface Props {
  value: string;
  onChange: (text: string) => void;
  onResolved?: (parts: ResolvedAddress) => void;
  /** ISO2 to bias/scope suggestions (e.g. the checkout's destination country). */
  countryCode?: string;
  placeholder?: string;
  required?: boolean;
  name?: string;
}

const AddressAutocompleteInput: React.FC<Props> = ({ value, onChange, onResolved, countryCode, placeholder, required, name }) => {
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [open, setOpen] = useState(false);
  const debounceRef = useRef<number | null>(null);
  const seqRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    loadSiteConfig().then(cfg => {
      if (!cancelled) setApiKey(cfg.placesApiKey);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const query = (input: string) => {
    const seq = ++seqRef.current;
    const body: Record<string, unknown> = { input, languageCode: 'en' };
    if (countryCode) body.includedRegionCodes = [countryCode];
    fetch('https://places.googleapis.com/v1/places:autocomplete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Goog-Api-Key': apiKey! },
      body: JSON.stringify(body),
    })
      .then(r => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then((data: { suggestions?: { placePrediction?: { placeId?: string; text?: { text?: string } } }[] }) => {
        if (seq !== seqRef.current) return; // stale response
        const list = (data.suggestions ?? [])
          .map(s => s.placePrediction)
          .filter((p): p is NonNullable<typeof p> => !!p?.placeId)
          .map(p => ({ placeId: p.placeId!, text: p.text?.text ?? '' }))
          .filter(s => s.text);
        setSuggestions(list.slice(0, 5));
        setOpen(list.length > 0);
      })
      .catch(() => {
        setSuggestions([]);
        setOpen(false);
      });
  };

  const handleChange = (text: string) => {
    onChange(text);
    if (!apiKey) return;
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    if (text.trim().length < 3) {
      setSuggestions([]);
      setOpen(false);
      return;
    }
    debounceRef.current = window.setTimeout(() => query(text.trim()), 300);
  };

  const pick = (s: Suggestion) => {
    setOpen(false);
    setSuggestions([]);
    onChange(s.text);
    if (!onResolved) return;
    fetch(`https://places.googleapis.com/v1/places/${encodeURIComponent(s.placeId)}`, {
      headers: { 'X-Goog-Api-Key': apiKey!, 'X-Goog-FieldMask': 'addressComponents' },
    })
      .then(r => (r.ok ? r.json() : Promise.reject(new Error(String(r.status)))))
      .then((data: { addressComponents?: { longText?: string; shortText?: string; types?: string[] }[] }) => {
        const comp = (type: string) => data.addressComponents?.find(c => c.types?.includes(type));
        const streetNumber = comp('street_number')?.longText ?? '';
        const route = comp('route')?.longText ?? '';
        const line = [route, streetNumber].filter(Boolean).join(' ') || s.text;
        onResolved({
          line,
          city: comp('postal_town')?.longText ?? comp('locality')?.longText ?? undefined,
          region: comp('administrative_area_level_1')?.longText ?? undefined,
          postalCode: comp('postal_code')?.longText ?? undefined,
          countryCode: comp('country')?.shortText ?? undefined,
        });
      })
      .catch(() => onResolved({ line: s.text })); // details failed: keep the picked text
  };

  return (
    <div className='position-relative'>
      <Form.Control
        name={name}
        value={value}
        onChange={e => handleChange(e.target.value)}
        onBlur={() => window.setTimeout(() => setOpen(false), 200)}
        placeholder={placeholder}
        required={required}
        autoComplete='street-address'
      />
      {open && (
        <div className='position-absolute bg-white border rounded shadow-sm mt-1 w-100' style={{ zIndex: 1060 }} role='listbox'>
          {suggestions.map(s => (
            <button
              key={s.placeId}
              type='button'
              role='option'
              aria-selected={false}
              className='dropdown-item text-wrap small py-2'
              onMouseDown={e => e.preventDefault()}
              onClick={() => pick(s)}
            >
              <i className='bi bi-geo-alt me-1 text-muted' />
              {s.text}
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export default AddressAutocompleteInput;
