import { beforeEach, describe, expect, it, jest } from '@jest/globals';
import type { Mock } from 'jest-mock';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import * as React from 'react';

// The RTK hooks are mocked at the module seam so the component is exercised against
// envelopes, not against fetch. What is under test is the page's own behaviour: which rows
// the header checkbox sweeps up, what Apply sends, and that every server message — refusal
// or stale-index caveat — reaches the screen verbatim.
jest.mock('app/shared/reducers/private/services/adminSeoApi', () => ({
  SEO_BATCH_LIMIT: 100,
  useGetSeoTitlesQuery: jest.fn(),
  useApplySeoTitleMutation: jest.fn(),
  useApplySeoTitlesMutation: jest.fn(),
}));

import * as api from 'app/shared/reducers/private/services/adminSeoApi';
import SeoTitleList from './SeoTitleList';

type AnyMock = Mock<any>;

const useGet = api.useGetSeoTitlesQuery as unknown as AnyMock;
const useApply = api.useApplySeoTitleMutation as unknown as AnyMock;
const useApplyBatch = api.useApplySeoTitlesMutation as unknown as AnyMock;

const LONG = 'Rechargeable Cordless Garden Hedge Trimmer With Two Batteries And A Carry Case For Home Use';

const row = (over: Partial<api.ISeoTitleRow> = {}): api.ISeoTitleRow => ({
  goodsId: 1,
  category: 'Garden Tools',
  currentTitle: LONG,
  currentLength: LONG.length,
  proposedTitle: 'Rechargeable Cordless Garden Hedge Trimmer With Two',
  proposedLength: 51,
  needsReview: false,
  matchedKeyword: 'hedge trimmer',
  matchedKeywordVolume: 12100,
  keywordSurvives: true,
  terms: [{ term: 'hedge trimmer', monthlySearches: 12100 }],
  ...over,
});

const ok = (data: unknown) => ({ data: { errno: 0, errmsg: 'success', data } });

let applyFn: AnyMock;
let applyBatchFn: AnyMock;

const wire = (rows: api.ISeoTitleRow[], extra: Partial<{ isLoading: boolean; isError: boolean }> = {}) => {
  useGet.mockReturnValue({
    data: { total: rows.length, scanned: 10, maxLength: 60, list: rows },
    isLoading: false,
    isFetching: false,
    isError: false,
    ...extra,
  });
  applyFn = jest.fn(() => Promise.resolve(ok({ goodsId: 1, title: 'x', changed: true, reindexed: true })));
  applyBatchFn = jest.fn(() => Promise.resolve(ok({ applied: 0, failed: 0, results: [] })));
  useApply.mockReturnValue([applyFn, { isLoading: false }]);
  useApplyBatch.mockReturnValue([applyBatchFn, { isLoading: false }]);
};

beforeEach(() => {
  useGet.mockReset();
  useApply.mockReset();
  useApplyBatch.mockReset();
});

describe('SeoTitleList — what it shows', () => {
  it('renders each over-length row with its length, the proposal and the demand evidence', () => {
    wire([row(), row({ goodsId: 2, needsReview: true, proposedTitle: LONG, keywordSurvives: false })]);
    render(<SeoTitleList />);

    // Two current-title cells, plus the flagged row's textarea (its proposal IS the original).
    expect(screen.getAllByText(LONG).length).toBe(3);
    // Two current-title badges, plus the flagged row's draft — the server kept its original.
    expect(screen.getAllByText(`${LONG.length} chars`).length).toBe(3);
    expect(screen.getByText('2 of 10 live products are over.', { exact: false })).toBeTruthy();
    // The row badge; the explanatory paragraph also says the words inside an <em>.
    expect(screen.getByText('needs review', { selector: ':not(em)' })).toBeTruthy();
    expect(screen.getAllByText('hedge trimmer').length).toBeGreaterThan(0);
    expect(screen.getAllByText('12,100/mo').length).toBe(2);
    expect(screen.getByText('dropped by the shortening')).toBeTruthy();
    const box = screen.getByLabelText('Proposed title for goods 1') as HTMLTextAreaElement;
    expect(box.value).toBe('Rechargeable Cordless Garden Hedge Trimmer With Two');
  });

  it('names the threshold in the empty state', () => {
    wire([]);
    render(<SeoTitleList />);
    expect(screen.getByText('No live product titles over 60 characters.')).toBeTruthy();
  });

  it('says so when the worklist cannot load', () => {
    wire([], { isError: true });
    render(<SeoTitleList />);
    expect(screen.getByText('Failed to load the title worklist.')).toBeTruthy();
  });
});

describe('SeoTitleList — single apply', () => {
  it('disables Apply when the proposal is the current title (server kept the original)', () => {
    wire([row({ needsReview: true, proposedTitle: LONG })]);
    render(<SeoTitleList />);
    expect((screen.getByRole('button', { name: 'Apply' }) as HTMLButtonElement).disabled).toBe(true);
  });

  it('sends exactly the edited draft and marks the row applied', async () => {
    wire([row()]);
    render(<SeoTitleList />);
    fireEvent.change(screen.getByLabelText('Proposed title for goods 1'), { target: { value: '  Cordless Hedge Trimmer, 2 batteries  ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(screen.getByText('applied')).toBeTruthy());
    expect(applyFn).toHaveBeenCalledWith({ goodsId: 1, title: 'Cordless Hedge Trimmer, 2 batteries' });
    expect(screen.queryByRole('button', { name: 'Apply' })).toBeNull();
  });

  it('refuses a blank draft locally without calling the server', () => {
    wire([row()]);
    render(<SeoTitleList />);
    const box = screen.getByLabelText('Proposed title for goods 1');
    fireEvent.change(box, { target: { value: '   ' } });
    // Blank is unapplicable, so the button is disabled — nothing reaches the server.
    expect((screen.getByRole('button', { name: 'Apply' }) as HTMLButtonElement).disabled).toBe(true);
    expect(applyFn).not.toHaveBeenCalled();
  });

  it('shows a server refusal verbatim and keeps the row editable', async () => {
    wire([row()]);
    applyFn.mockImplementation(() => Promise.resolve({ data: { errno: 660, errmsg: 'title must be at most 127 characters' } }));
    render(<SeoTitleList />);
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(screen.getByText('title must be at most 127 characters')).toBeTruthy());
    expect(screen.queryByText('applied')).toBeNull();
    expect((screen.getByRole('button', { name: 'Apply' }) as HTMLButtonElement).disabled).toBe(false);
  });

  it('treats a stale index as a success WITH a warning, not as a failure to retry', async () => {
    wire([row()]);
    applyFn.mockImplementation(() =>
      Promise.resolve(
        ok({ goodsId: 1, title: 'x', changed: true, reindexed: false, warning: 'Saved, but on-site search still shows the old title: indexer down' }),
      ),
    );
    render(<SeoTitleList />);
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }));

    await waitFor(() => expect(screen.getByText('saved, not reindexed')).toBeTruthy());
    expect(screen.getByText('#1: Saved, but on-site search still shows the old title: indexer down')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Apply' })).toBeNull();
  });
});

describe('SeoTitleList — bulk apply', () => {
  const three = () => [row({ goodsId: 1 }), row({ goodsId: 2, needsReview: true }), row({ goodsId: 3 })];

  it('the header checkbox selects only rows that do not need review', () => {
    wire(three());
    render(<SeoTitleList />);
    expect(screen.getByRole('button', { name: 'Apply selected (0)' })).toBeTruthy();

    fireEvent.click(screen.getByLabelText('Select all rows on this page that do not need review'));

    expect((screen.getByLabelText('Select goods 1') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText('Select goods 2') as HTMLInputElement).checked).toBe(false);
    expect((screen.getByLabelText('Select goods 3') as HTMLInputElement).checked).toBe(true);
    expect(screen.getByRole('button', { name: 'Apply selected (2)' })).toBeTruthy();
  });

  it('a flagged row can still be ticked by hand', () => {
    wire(three());
    render(<SeoTitleList />);
    fireEvent.click(screen.getByLabelText('Select goods 2'));
    expect(screen.getByRole('button', { name: 'Apply selected (1)' })).toBeTruthy();
  });

  it('asks before applying, then sends the ticked drafts and reports every row of the answer', async () => {
    wire(three());
    applyBatchFn.mockImplementation(() =>
      Promise.resolve(
        ok({
          applied: 1,
          failed: 1,
          results: [
            { goodsId: 1, ok: true, title: 'A', changed: true, reindexed: true, error: null },
            { goodsId: 3, ok: false, title: null, changed: false, reindexed: false, error: 'no such goods: 3' },
          ],
        }),
      ),
    );
    render(<SeoTitleList />);
    fireEvent.click(screen.getByLabelText('Select all rows on this page that do not need review'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply selected (2)' }));

    // Nothing sent yet — the confirm line is the gate.
    expect(applyBatchFn).not.toHaveBeenCalled();
    expect(screen.getByText('Rename 2 products and reindex them for on-site search?', { exact: false })).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    await waitFor(() => expect(screen.getByText('Applied 1 of 2 titles.')).toBeTruthy());
    expect(applyBatchFn).toHaveBeenCalledWith({
      items: [
        { goodsId: 1, title: 'Rechargeable Cordless Garden Hedge Trimmer With Two' },
        { goodsId: 3, title: 'Rechargeable Cordless Garden Hedge Trimmer With Two' },
      ],
    });
    expect(screen.getByText('#3: no such goods: 3')).toBeTruthy();
    // The refused row shows its error in place and stays ticked for a retry.
    expect(screen.getByText('no such goods: 3')).toBeTruthy();
    expect((screen.getByLabelText('Select goods 3') as HTMLInputElement).checked).toBe(true);
    expect((screen.getByLabelText('Select goods 1') as HTMLInputElement).checked).toBe(false);
  });

  it('Cancel on the confirm line sends nothing', () => {
    wire(three());
    render(<SeoTitleList />);
    fireEvent.click(screen.getByLabelText('Select goods 1'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply selected (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(applyBatchFn).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Apply selected (1)' })).toBeTruthy();
  });

  it('a transport failure on the batch is reported, not swallowed', async () => {
    wire(three());
    applyBatchFn.mockImplementation(() => Promise.resolve({ error: { status: 502, data: 'bad gateway' } }));
    render(<SeoTitleList />);
    fireEvent.click(screen.getByLabelText('Select goods 1'));
    fireEvent.click(screen.getByRole('button', { name: 'Apply selected (1)' }));
    fireEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(screen.getByText('Request failed.')).toBeTruthy());
  });
});
