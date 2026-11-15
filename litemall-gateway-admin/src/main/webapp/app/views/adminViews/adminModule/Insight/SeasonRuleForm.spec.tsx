import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import type { Mock } from 'jest-mock';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import * as React from 'react';

jest.mock('app/shared/reducers/private/services/insightApi', () => ({
  useUpdateSeasonRuleMutation: jest.fn(),
}));

import * as api from 'app/shared/reducers/private/services/insightApi';
import SeasonRuleForm from './SeasonRuleForm';

type AnyMock = Mock<any>;
const useUpdate = api.useUpdateSeasonRuleMutation as unknown as AnyMock;

const RULE: api.ISeasonRule = {
  seasonKey: 'autumn',
  name: 'Autumn',
  windowStartMd: '09-01',
  windowEndMd: '11-30',
  terms: '["autumn","blanket","-summer"]',
  categoryIds: '[]',
  priceMin: 5,
  priceMax: 500,
  weights: '{"euMultiplier":1.2,"freshnessBonusMax":1.25,"categoryBoost":1.15,"demandWeight":1}',
  enabled: true,
};

let updateFn: AnyMock;

beforeEach(() => {
  useUpdate.mockReset();
  updateFn = jest.fn(() => Promise.resolve({ data: { errno: 0, errmsg: 'success' } }));
  useUpdate.mockReturnValue([updateFn, { isLoading: false }]);
});

const save = () => fireEvent.click(screen.getByRole('button', { name: 'Save changes' }));

describe('SeasonRuleForm', () => {
  it('shows match and exclude terms as separate lists without the prefix', () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    expect(screen.getByText('autumn')).toBeTruthy();
    expect(screen.getByText('summer')).toBeTruthy();
    expect(screen.queryByText('-summer')).toBeNull();
  });

  it('sends nothing when nothing changed', () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    save();
    expect(updateFn.mock.calls.length).toBe(0);
    expect(screen.getByText('Nothing changed.')).toBeTruthy();
  });

  it('adds an exclusion and sends only the terms, serialised with the prefix', async () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    const input = screen.getByLabelText('Add to exclude terms');
    fireEvent.change(input, { target: { value: 'Cooling' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    save();
    await waitFor(() => expect(updateFn.mock.calls.length).toBe(1));
    expect(updateFn.mock.calls[0][0]).toEqual({ key: 'autumn', patch: { terms: '["autumn","blanket","-summer","-cooling"]' } });
    await waitFor(() => expect(screen.getByText(/takes effect at the next scoring run/)).toBeTruthy());
  });

  it('refuses to save a season with no match terms', () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    fireEvent.click(screen.getByRole('button', { name: 'Remove match terms autumn' }));
    fireEvent.click(screen.getByRole('button', { name: 'Remove match terms blanket' }));
    save();
    expect(updateFn.mock.calls.length).toBe(0);
    expect(screen.getByText(/At least one match term is required/)).toBeTruthy();
  });

  it('refuses a weight outside the scorer range', () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    fireEvent.change(screen.getByLabelText('Demand weight'), { target: { value: '0' } });
    save();
    expect(updateFn.mock.calls.length).toBe(0);
    expect(screen.getByText('Must be between 1.0 and 3.0.')).toBeTruthy();
  });

  it('refuses category ids that are not whole numbers', () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    fireEvent.change(screen.getByLabelText('Boosted category ids'), { target: { value: '1036143, garden' } });
    save();
    expect(updateFn.mock.calls.length).toBe(0);
    expect(screen.getByText(/Category ids must be whole numbers/)).toBeTruthy();
  });

  it('sends category ids as a JSON array', async () => {
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    fireEvent.change(screen.getByLabelText('Boosted category ids'), { target: { value: '1036143, 1036495, 1036143' } });
    save();
    await waitFor(() => expect(updateFn.mock.calls.length).toBe(1));
    expect(updateFn.mock.calls[0][0]).toEqual({ key: 'autumn', patch: { categoryIds: '[1036143,1036495]' } });
  });

  it('says a failed save changed nothing', async () => {
    updateFn.mockImplementation(() => Promise.resolve({ error: { status: 502 } }));
    render(<SeasonRuleForm rule={RULE} onClose={() => undefined} />);
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'Autumn 2026' } });
    save();
    await waitFor(() => expect(screen.getByText('Save failed (502). Nothing was changed.')).toBeTruthy());
  });
});
