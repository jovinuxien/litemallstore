import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import type { Mock } from 'jest-mock';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import * as React from 'react';
import { MemoryRouter } from 'react-router-dom';

// RTK hooks mocked at the module seam (SeoTitleList.spec precedent): what is
// under test is the page — tabs from the rules, the veto/restore confirm, the
// server's refusal verbatim, the run summary, and honest error states.
jest.mock('app/shared/reducers/private/services/insightApi', () => ({
  useGetSeasonRulesQuery: jest.fn(),
  useGetSeasonCandidatesQuery: jest.fn(),
  useDismissSeasonCandidateMutation: jest.fn(),
  useRestoreSeasonCandidateMutation: jest.fn(),
  useRunSeasonCandidatesMutation: jest.fn(),
}));

import * as api from 'app/shared/reducers/private/services/insightApi';
import SeasonCandidateList from './SeasonCandidateList';

type AnyMock = Mock<any>;
const useRules = api.useGetSeasonRulesQuery as unknown as AnyMock;
const useCandidates = api.useGetSeasonCandidatesQuery as unknown as AnyMock;
const useDismiss = api.useDismissSeasonCandidateMutation as unknown as AnyMock;
const useRestore = api.useRestoreSeasonCandidateMutation as unknown as AnyMock;
const useRun = api.useRunSeasonCandidatesMutation as unknown as AnyMock;

const ok = (data?: unknown) => ({ data: { errno: 0, errmsg: 'success', data } });

const candidate = (over: Partial<api.ISeasonCandidate> = {}): api.ISeasonCandidate => ({
  goodsId: 10010060,
  seasonKey: 'autumn',
  day: '2026-11-15',
  tier: 'hot',
  score: 812.44,
  status: 'auto',
  reasons: '["term: wool"]',
  name: 'Wool Throw Blanket',
  retailPrice: 34.5,
  cost: 13.8,
  marginPct: 60,
  ...over,
});

let dismissFn: AnyMock;
let restoreFn: AnyMock;
let runFn: AnyMock;

const wire = (rows: api.ISeasonCandidate[], extra: Record<string, unknown> = {}) => {
  useRules.mockReturnValue({
    data: [
      { seasonKey: 'autumn', name: 'Autumn', enabled: true },
      { seasonKey: 'winter', name: 'Winter', enabled: false },
    ],
    isLoading: false,
    isError: false,
  });
  useCandidates.mockReturnValue({ data: { seasonKey: 'autumn', day: '2026-11-15', list: rows }, isLoading: false, isFetching: false, isError: false, ...extra });
  dismissFn = jest.fn(() => Promise.resolve(ok()));
  restoreFn = jest.fn(() => Promise.resolve(ok()));
  runFn = jest.fn(() => Promise.resolve(ok({ seasons: [] })));
  useDismiss.mockReturnValue([dismissFn, { isLoading: false }]);
  useRestore.mockReturnValue([restoreFn, { isLoading: false }]);
  useRun.mockReturnValue([runFn, { isLoading: false }]);
};

const show = () =>
  render(
    <MemoryRouter>
      <SeasonCandidateList />
    </MemoryRouter>,
  );

beforeEach(() => {
  [useRules, useCandidates, useDismiss, useRestore, useRun].forEach(m => m.mockReset());
});

describe('SeasonCandidateList', () => {
  it('builds the tabs from the rules and queries the first season', () => {
    wire([candidate()]);
    show();
    expect(screen.getByRole('button', { name: /Autumn/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Winter/ })).toBeTruthy();
    expect(useCandidates.mock.calls[0][0]).toEqual({ season: 'autumn', day: undefined, status: undefined });
    expect(screen.getByText('Wool Throw Blanket')).toBeTruthy();
    // 'Published' is also a filter option — assert it on the row itself.
    expect((screen.getByText('Wool Throw Blanket').closest('tr') as HTMLElement).textContent).toContain('Published');
  });

  it('switches season when a tab is clicked', () => {
    wire([candidate()]);
    show();
    fireEvent.click(screen.getByRole('button', { name: /Winter/ }));
    const last = useCandidates.mock.calls[useCandidates.mock.calls.length - 1][0] as { season: string };
    expect(last.season).toBe('winter');
  });

  it('renders an uncaptured cost and margin as a dash, never zero', () => {
    wire([candidate({ cost: null, marginPct: null })]);
    show();
    const row = screen.getByText('Wool Throw Blanket').closest('tr') as HTMLElement;
    expect(row.textContent).toContain('—');
    expect(row.textContent).not.toContain('0.0%');
  });

  it('dismisses only after the inline confirm, sending season and day', async () => {
    wire([candidate()]);
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(dismissFn.mock.calls.length).toBe(0);
    fireEvent.click(screen.getByRole('button', { name: 'Confirm dismiss' }));
    await waitFor(() => expect(dismissFn.mock.calls.length).toBe(1));
    expect(dismissFn.mock.calls[0][0]).toEqual({ goodsId: 10010060, season: 'autumn', day: '2026-11-15' });
  });

  it('offers Restore, not Dismiss, on a vetoed row', async () => {
    wire([candidate({ status: 'dismissed' })]);
    show();
    expect(screen.queryByRole('button', { name: 'Dismiss' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Restore' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm restore' }));
    await waitFor(() => expect(restoreFn.mock.calls.length).toBe(1));
  });

  it('shows a lost-race refusal in the server words', async () => {
    wire([candidate()]);
    dismissFn.mockImplementation(() =>
      Promise.resolve({ data: { errno: 653, errmsg: 'this candidate was already decided or no longer exists' } }),
    );
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Dismiss' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm dismiss' }));
    await waitFor(() => expect(screen.getByText(/this candidate was already decided or no longer exists/)).toBeTruthy());
  });

  it('runs the scorer only after confirming and shows the per-season summary', async () => {
    wire([candidate()]);
    runFn.mockImplementation(() =>
      Promise.resolve(
        ok({
          seasons: [
            {
              seasonKey: 'autumn',
              scanned: 1200,
              scored: 1005,
              published: 352,
              droppedByCap: 0,
              rejections: { uncosted: 40 },
              discovery: { discardedRelaxed: 0, discardedOffTitle: 914, discardedExcluded: 44 },
              tierCuts: { hot: 773.3, featured: 631.67 },
            },
          ],
        }),
      ),
    );
    show();
    fireEvent.click(screen.getByRole('button', { name: 'Run scorer now' }));
    expect(runFn.mock.calls.length).toBe(0);
    fireEvent.click(screen.getByRole('button', { name: 'Run scorer' }));
    await waitFor(() => expect(screen.getByText('Scoring run finished')).toBeTruthy());
    expect(screen.getByText('0 / 914 / 44')).toBeTruthy();
    expect(screen.getByText('773.3 / 631.7')).toBeTruthy();
    expect(screen.getByText('uncosted 40')).toBeTruthy();
  });

  it('shows an error banner — not an empty table — when the list fails', () => {
    wire([], { isError: true, error: { status: 502 }, data: undefined });
    show();
    expect(screen.getByText(/Failed to load season candidates \(502\)/)).toBeTruthy();
    expect(screen.queryByText(/has not been scored yet/)).toBeNull();
  });

  it('pages a long season client-side', () => {
    wire(Array.from({ length: 120 }, (_, i) => candidate({ goodsId: 1000 + i, name: `Product ${i}` })));
    show();
    expect(screen.getByText(/Page 1 of 3/)).toBeTruthy();
    expect(screen.queryByText('Product 50')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText('Product 50')).toBeTruthy();
  });
});
