import React from 'react';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * The address book's Country, Phone and Region fields as one customer would use
 * them. Checkout's delivery form applies the SAME rules (shared/util/deliverySync)
 * through the same PhoneInput, so this is the rendered proof for both.
 */
const mockAddressDetail = jest.fn();
jest.mock('app/shared/api', () => ({
  contentApi: { regionCList: jest.fn(() => Promise.resolve([])) },
  userApi: {
    addressDetail: (id: number) => mockAddressDetail(id),
    addressSave: jest.fn(() => Promise.resolve(1)),
    addressDelete: jest.fn(() => Promise.resolve()),
  },
}));
jest.mock('app/shared/config/siteConfig', () => ({
  loadSiteConfig: jest.fn(() => Promise.resolve({ placesApiKey: null })),
}));

import AddressEdit from './AddressEdit';

const renderAt = async (path: string) => {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path='/user/address/:id' element={<AddressEdit />} />
      </Routes>
    </MemoryRouter>
  );
  await act(async () => {
    await Promise.resolve();
    await Promise.resolve();
  });
};

const countrySelect = () => document.querySelector('select') as HTMLSelectElement;
const dial = () => screen.getByLabelText('Select country code').textContent?.match(/\+\d+/)?.[0];
const regionField = () => document.querySelector('input[autocomplete=address-level1]') as HTMLInputElement;
const selectCountry = (code: string) => fireEvent.change(countrySelect(), { target: { value: code } });
const pickDialCode = (name: string) => {
  fireEvent.click(screen.getByLabelText('Select country code'));
  const dropdown = document.querySelector('div[role=listbox]') as HTMLElement;
  fireEvent.click(within(dropdown).getByRole('option', { name: new RegExp(name) }));
};

describe('AddressEdit country, phone and region sync', () => {
  beforeEach(() => mockAddressDetail.mockReset());

  it('pre-selects the country of the phone default on a new address', async () => {
    await renderAt('/user/address/new'); // jsdom browser language is en-US
    expect(dial()).toBe('+1');
    expect(countrySelect().value).toBe('US');
  });

  it('moves the dial code when a country is selected', async () => {
    await renderAt('/user/address/new');
    selectCountry('DE');
    expect(dial()).toBe('+49');
    selectCountry('SE');
    expect(dial()).toBe('+46');
  });

  it('moves the country when a dial code is picked, and back again', async () => {
    await renderAt('/user/address/new');
    selectCountry('DE');
    pickDialCode('France');
    expect(countrySelect().value).toBe('FR');
    selectCountry('SE');
    expect(dial()).toBe('+46');
    expect(countrySelect().value).toBe('SE');
  });

  it('keeps the country when the dial code is for a place we do not ship to', async () => {
    await renderAt('/user/address/new');
    selectCountry('SE');
    pickDialCode('Japan');
    expect(dial()).toBe('+81');
    expect(countrySelect().value).toBe('SE');
    // …and says so, rather than leaving the two fields silently out of step.
    expect(document.querySelector('[data-delivery-note]')?.textContent).toContain('We don’t deliver to Japan yet, so the delivery country stays Sweden');
  });

  it('clears a region that belongs to the country that was left', async () => {
    await renderAt('/user/address/new');
    selectCountry('DE');
    fireEvent.change(regionField(), { target: { value: 'Bayern' } });
    selectCountry('SE');
    expect(regionField().value).toBe('');
    // …whether the country moved through the select or through the phone.
    fireEvent.change(regionField(), { target: { value: 'Skåne' } });
    pickDialCode('Germany');
    expect(countrySelect().value).toBe('DE');
    expect(regionField().value).toBe('');
  });

  it('a region typed while Country is empty names the country, and the dial code follows', async () => {
    await renderAt('/user/address/new');
    selectCountry(''); // the jsdom browser is en-US; start from no country
    expect(countrySelect().value).toBe('');
    fireEvent.change(regionField(), { target: { value: 'Bayern' } });
    expect(countrySelect().value).toBe('DE');
    expect(dial()).toBe('+49');
    expect(regionField().value).toBe('Bayern');
  });

  it('a pick from the cross-country region list sets the country', async () => {
    await renderAt('/user/address/new');
    selectCountry('');
    fireEvent.change(regionField(), { target: { value: 'hoved' } });
    const dropdown = regionField().parentElement!.querySelector('div[role=listbox]') as HTMLElement;
    fireEvent.click(within(dropdown).getByRole('option', { name: 'Hovedstaden · Denmark' }));
    expect(countrySelect().value).toBe('DK');
    expect(dial()).toBe('+45');
    expect(regionField().value).toBe('Hovedstaden');
  });

  it('a region never overrides a chosen country', async () => {
    await renderAt('/user/address/new');
    selectCountry('SE');
    fireEvent.change(regionField(), { target: { value: 'Bayern' } });
    expect(countrySelect().value).toBe('SE');
    expect(regionField().value).toBe('Bayern');
  });

  it('leaves a saved foreign number alone when the country of a saved address changes', async () => {
    mockAddressDetail.mockResolvedValue({ id: 7, name: 'A', tel: '+46701234567', countryCode: 'DE', province: 'Bayern' });
    await renderAt('/user/address/7');
    expect(countrySelect().value).toBe('DE'); // the stored number did not override the stored country
    expect(dial()).toBe('+46');
    selectCountry('FR');
    expect(dial()).toBe('+46');
    expect(regionField().value).toBe('');
  });
});
