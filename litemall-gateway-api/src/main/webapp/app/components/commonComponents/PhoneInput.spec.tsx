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
  fireEvent.click(dialButton());
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
