import React, { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';

import { deliveryRegions, regionsFor } from 'app/shared/data/regions';

import RegionInput from './RegionInput';

/**
 * The region type-ahead. With a country chosen it lists that country's regions; with
 * none chosen it lists EVERY delivery country's regions labelled with the country, and a
 * pick reports the country too — the form then fills Country from it.
 */
const field = () => document.querySelector('input[autocomplete=address-level1]') as HTMLInputElement;
const list = () => document.querySelector('div[role=listbox]') as HTMLElement | null;
const type = (text: string) => fireEvent.change(field(), { target: { value: text } });

const Harness: React.FC<{ country?: string; onPick?: (region: string, country?: string) => void }> = ({ country, onPick }) => {
  const [value, setValue] = useState('');
  return <RegionInput value={value} onChange={setValue} suggestions={regionsFor(country)} labelled={country ? undefined : deliveryRegions()} onPick={onPick} />;
};

describe('RegionInput', () => {
  it('with a country chosen lists only that country’s regions, unlabelled', () => {
    render(<Harness country='SE' />);
    fireEvent.focus(field());
    const names = within(list()!).getAllByRole('option').map(o => o.textContent);
    expect(names).toContain('Skåne');
    expect(names).not.toContain('Bayern');
    expect(names.some(n => n?.includes(' · '))).toBe(false);
  });

  it('with no country lists every delivery country’s regions, labelled with the country', () => {
    render(<Harness />);
    fireEvent.focus(field());
    const names = within(list()!).getAllByRole('option').map(o => o.textContent);
    expect(names).toContain('Skåne · Sweden');
    expect(names).toContain('Bayern · Germany');
    expect(names).toContain('Sjælland · Denmark');
    expect(names.length).toBe(deliveryRegions().length);
  });

  it('typing narrows across countries, on region or country name', () => {
    render(<Harness />);
    type('sjæ'); // æ is a letter of its own, not an accent — the fold keeps it
    expect(within(list()!).getAllByRole('option').map(o => o.textContent)).toEqual(['Sjælland · Denmark']);
    type('vastra'); // whereas ä folds: "Västra Götaland" is found without the umlaut
    expect(within(list()!).getAllByRole('option').map(o => o.textContent)).toEqual(['Västra Götaland · Sweden']);
    type('denm');
    expect(within(list()!).getAllByRole('option').length).toBe(5);
  });

  it('a cross-country pick reports the region’s own spelling AND its country', () => {
    const onPick = jest.fn();
    render(<Harness onPick={onPick} />);
    type('bay');
    fireEvent.click(within(list()!).getByRole('option', { name: 'Bayern · Germany' }));
    expect(onPick).toHaveBeenCalledWith('Bayern', 'DE');
    expect(list()).toBeNull();
  });

  it('a same-country pick reports no country, and falls back to onChange without onPick', () => {
    const onPick = jest.fn();
    const { unmount } = render(<Harness country='SE' onPick={onPick} />);
    type('ska');
    fireEvent.click(within(list()!).getByRole('option', { name: 'Skåne' }));
    expect(onPick).toHaveBeenCalledWith('Skåne', undefined);
    unmount();

    render(<Harness country='SE' />);
    type('ska');
    fireEvent.click(within(list()!).getByRole('option', { name: 'Skåne' }));
    expect(field().value).toBe('Skåne');
  });

  it('an exact match leaves nothing to suggest; free text stays valid', () => {
    render(<Harness />);
    type('Bayern');
    expect(list()).toBeNull();
    type('Atlantis');
    expect(list()).toBeNull();
    expect(field().value).toBe('Atlantis');
    expect(screen.queryByRole('option')).toBeNull();
  });
});
