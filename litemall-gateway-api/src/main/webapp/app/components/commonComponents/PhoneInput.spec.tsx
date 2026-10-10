import React, { useState } from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';

import PhoneInput from './PhoneInput';

/**
 * The phone field's side of the Country ⇄ Mobile sync. The dial-code button shows
 * the selected country (`+46`), the text field holds the national digits, and
 * `onChange` receives the composed E.164 number.
 */
const dialButton = () => screen.getByLabelText('Select country code');
const dial = () => dialButton().textContent?.match(/\+\d+/)?.[0];
const numberField = () => document.querySelector('input[type=tel]') as HTMLInputElement;
const type = (text: string) => fireEvent.change(numberField(), { target: { value: text } });
const pickCountry = (name: string) => {
  if (!document.querySelector('div[role=listbox]')) fireEvent.click(dialButton());
  // Scoped to the dial-code dropdown: a form's own Country <select> has options too.
  const dropdown = document.querySelector('div[role=listbox]') as HTMLElement;
  fireEvent.click(within(dropdown).getByRole('option', { name: new RegExp(name) }));
};

describe('PhoneInput country sync', () => {
  it('starts on the form country and reports it as a start-up default', () => {
    const onCountryChange = jest.fn();
    render(<PhoneInput onChange={jest.fn()} countryIso2='DE' onCountryChange={onCountryChange} />);
    expect(dial()).toBe('+49');
    expect(onCountryChange).toHaveBeenCalledTimes(1);
    expect(onCountryChange).toHaveBeenCalledWith('DE', 'init');
  });

  it('follows the form country when it changes, keeping the digits', () => {
    const onChange = jest.fn();
    const onCountryChange = jest.fn();
    const { rerender } = render(<PhoneInput onChange={onChange} countryIso2='SE' onCountryChange={onCountryChange} />);
    type('0701234567');
    expect(onChange).toHaveBeenLastCalledWith('+46701234567');

    rerender(<PhoneInput onChange={onChange} countryIso2='DE' onCountryChange={onCountryChange} />);
    expect(dial()).toBe('+49');
    expect(numberField().value).toBe('0701234567');
    expect(onChange).toHaveBeenLastCalledWith('+49701234567');
    // Reported as `external`, so the form does not treat its own change as a new pick.
    expect(onCountryChange).toHaveBeenLastCalledWith('DE', 'external');
  });

  it('reports a dial code chosen in the selector as a pick', () => {
    const onChange = jest.fn();
    const onCountryChange = jest.fn();
    render(<PhoneInput onChange={onChange} countryIso2='SE' onCountryChange={onCountryChange} />);
    type('701234567');
    pickCountry('Germany');
    expect(dial()).toBe('+49');
    expect(onCountryChange).toHaveBeenLastCalledWith('DE', 'pick');
    expect(onChange).toHaveBeenLastCalledWith('+49701234567');
  });

  it('reports a number typed in full international form as typed', () => {
    const onCountryChange = jest.fn();
    render(<PhoneInput onChange={jest.fn()} countryIso2='SE' onCountryChange={onCountryChange} />);
    type('+49 170 1234567');
    expect(dial()).toBe('+49');
    expect(onCountryChange).toHaveBeenLastCalledWith('DE', 'typed');
  });

  it('does not re-prefix a number typed in full when the form country changes', () => {
    const onChange = jest.fn();
    const { rerender } = render(<PhoneInput onChange={onChange} countryIso2='DE' />);
    type('+46 70 123 45 67');
    fireEvent.blur(numberField()); // folds into selector +46 and national digits
    expect(dial()).toBe('+46');
    onChange.mockClear();

    rerender(<PhoneInput onChange={onChange} countryIso2='FR' />);
    expect(dial()).toBe('+46');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('keeps a stored number on its own country at mount and when the country changes', () => {
    const onChange = jest.fn();
    const { rerender } = render(<PhoneInput value='+46701234567' onChange={onChange} countryIso2='DE' />);
    expect(dial()).toBe('+46');
    rerender(<PhoneInput value='+46701234567' onChange={onChange} countryIso2='FR' />);
    expect(dial()).toBe('+46');
    expect(onChange).not.toHaveBeenCalled();
  });

  it('follows again once the customer picks a dial code or clears the number', () => {
    const { rerender } = render(<PhoneInput onChange={jest.fn()} countryIso2='DE' />);
    type('+46 70 123 45 67');
    type('');
    rerender(<PhoneInput onChange={jest.fn()} countryIso2='FR' />);
    expect(dial()).toBe('+33');
  });

  it('ignores a country it has no dial code for', () => {
    const { rerender } = render(<PhoneInput onChange={jest.fn()} countryIso2='SE' />);
    rerender(<PhoneInput onChange={jest.fn()} countryIso2='ZZ' />);
    expect(dial()).toBe('+46');
    rerender(<PhoneInput onChange={jest.fn()} countryIso2={undefined} />);
    expect(dial()).toBe('+46');
  });

  it('settles with a parent that mirrors the country both ways (no ping-pong)', () => {
    // The shape checkout and the address book use: one country state, fed to the
    // phone and written from it. A pick must land once and stay put.
    const seen: string[] = [];
    const Form: React.FC = () => {
      const [country, setCountry] = useState('SE');
      return (
        <>
          <PhoneInput
            onChange={jest.fn()}
            countryIso2={country}
            onCountryChange={(iso2, source) => {
              seen.push(`${iso2}:${source}`);
              if (source === 'pick') setCountry(iso2);
            }}
          />
          <select aria-label='country' value={country} onChange={e => setCountry(e.target.value)}>
            <option value='SE'>Sweden</option>
            <option value='DE'>Germany</option>
            <option value='FR'>France</option>
          </select>
        </>
      );
    };
    render(<Form />);
    const select = screen.getByLabelText('country') as HTMLSelectElement;

    pickCountry('Germany');
    expect(select.value).toBe('DE');
    expect(dial()).toBe('+49');

    act(() => {
      fireEvent.change(select, { target: { value: 'FR' } });
    });
    expect(dial()).toBe('+33');
    expect(select.value).toBe('FR');
    expect(seen).toEqual(['SE:init', 'DE:pick', 'FR:external']);
  });
});

/**
 * Only a dial code from the form's own country list can move its Country field. Before
 * this, the 8 delivery countries sat unmarked among ~200 and a pick outside them did
 * nothing and said nothing — reported from production as "the dial number does not
 * synchronize with the country".
 */
describe('PhoneInput delivery countries', () => {
  const DELIVERY = ['SE', 'DE', 'FR'];
  const note = () => document.querySelector('[data-delivery-note]')?.textContent ?? null;
  const openList = () => {
    fireEvent.click(dialButton());
    return document.querySelector('div[role=listbox]') as HTMLElement;
  };

  it('lists the delivery countries first, under their own heading', () => {
    render(<PhoneInput onChange={jest.fn()} countryIso2='SE' deliveryCountries={DELIVERY} />);
    const list = openList();
    expect(within(list).getByText('We deliver to')).toBeTruthy();
    expect(within(list).getByText('Other countries')).toBeTruthy();
    const names = within(list)
      .getAllByRole('option')
      .map(o => o.querySelector('.flex-grow-1')?.textContent);
    expect(names.slice(0, 3)).toEqual(['France', 'Germany', 'Sweden']);
    expect(names).toContain('Denmark');
    expect(names.indexOf('Denmark')).toBeGreaterThan(2);
    expect(new Set(names).size).toBe(names.length); // nobody is listed twice
  });

  it('searching shows one flat list of matches, no headings', () => {
    render(<PhoneInput onChange={jest.fn()} countryIso2='SE' deliveryCountries={DELIVERY} />);
    const list = openList();
    fireEvent.change(within(list).getByPlaceholderText('Search country…'), { target: { value: 'den' } });
    expect(within(list).queryByText('We deliver to')).toBeNull();
    expect(within(list).getByRole('option', { name: /Denmark/ })).toBeTruthy();
  });

  it('a pick outside the list says why the country did not follow', () => {
    const onCountryChange = jest.fn();
    render(<PhoneInput onChange={jest.fn()} countryIso2='SE' deliveryCountries={DELIVERY} onCountryChange={onCountryChange} />);
    expect(note()).toBeNull();
    pickCountry('Denmark');
    expect(dial()).toBe('+45');
    expect(onCountryChange).toHaveBeenLastCalledWith('DK', 'pick'); // the form decides; the field explains
    expect(note()).toContain('We don’t deliver to Denmark yet, so the delivery country stays Sweden');
  });

  it('asks for a delivery country when the form has none yet', () => {
    render(<PhoneInput onChange={jest.fn()} deliveryCountries={DELIVERY} defaultIso2='SE' />);
    pickCountry('Denmark');
    expect(note()).toContain('please choose a delivery country');
  });

  it('a number typed in full from outside the list gets the same note', () => {
    render(<PhoneInput onChange={jest.fn()} countryIso2='SE' deliveryCountries={DELIVERY} />);
    type('+45 12 34 56 78');
    expect(note()).toContain('Denmark');
  });

  it('says nothing for a delivery country, and the note goes once one is chosen', () => {
    const { rerender } = render(<PhoneInput onChange={jest.fn()} countryIso2='SE' deliveryCountries={DELIVERY} />);
    pickCountry('Germany');
    expect(note()).toBeNull();
    pickCountry('Denmark');
    expect(note()).not.toBeNull();
    // The customer then changes the form's country: the dial code follows it again.
    rerender(<PhoneInput onChange={jest.fn()} countryIso2='FR' deliveryCountries={DELIVERY} />);
    expect(dial()).toBe('+33');
    expect(note()).toBeNull();
  });

  it('a start-up default outside the list is not commented on', () => {
    // A Danish browser opens on +45 by default. Nobody chose that, so it earns no remark.
    render(<PhoneInput onChange={jest.fn()} defaultIso2='DK' deliveryCountries={DELIVERY} />);
    expect(dial()).toBe('+45');
    expect(note()).toBeNull();
  });

  it('a form with no country field (registration) keeps the plain list', () => {
    render(<PhoneInput onChange={jest.fn()} />);
    const list = openList();
    expect(within(list).queryByText('We deliver to')).toBeNull();
    pickCountry('Denmark');
    expect(note()).toBeNull();
  });
});
