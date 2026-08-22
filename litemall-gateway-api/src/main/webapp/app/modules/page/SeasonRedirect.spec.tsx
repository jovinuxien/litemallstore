import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

/**
 * Wave 27 — the old `/summer` bookmark. It used to render a hardcoded keyword
 * search; it now follows whatever season is active, and lands on the home page
 * when none is, rather than on an empty grid under a stale heading.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import { __resetSeason } from 'app/shared/util/season';

import SeasonRedirect from './SeasonRedirect';

const mockGet = baseAxios.get as jest.Mock;

const renderAt = () =>
  render(
    <MemoryRouter initialEntries={['/summer']}>
      <Routes>
        <Route path='/summer' element={<SeasonRedirect />} />
        <Route path='/page/:id' element={<div>season page</div>} />
        <Route path='/' element={<div>storefront home</div>} />
      </Routes>
    </MemoryRouter>
  );

beforeEach(() => {
  __resetSeason();
  mockGet.mockReset();
});

it('follows the running season to its page', async () => {
  mockGet.mockReturnValue(Promise.resolve({ data: { errno: 0, data: { id: 31, name: 'Autumn Deals', components: [] } } }));
  renderAt();
  expect(await screen.findByText('season page')).toBeTruthy();
});

it('lands on the storefront home when no season is running', async () => {
  mockGet.mockReturnValue(Promise.resolve({ data: { errno: 642, errmsg: 'no active season page' } }));
  renderAt();
  expect(await screen.findByText('storefront home')).toBeTruthy();
});

it('waits for the answer instead of bouncing the visitor home first', async () => {
  let release: (v: unknown) => void = () => undefined;
  mockGet.mockReturnValue(new Promise(res => (release = res)));
  const { container } = renderAt();
  await waitFor(() => expect(mockGet).toHaveBeenCalled());
  expect(screen.queryByText('storefront home')).toBeNull();
  expect(container.querySelector('.spinner-border')).toBeTruthy();
  release({ data: { errno: 0, data: { id: 31, name: 'Autumn Deals', components: [] } } });
  expect(await screen.findByText('season page')).toBeTruthy();
});
