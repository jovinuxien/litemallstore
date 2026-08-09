import React from 'react';
import { fireEvent, render, waitFor } from '@testing-library/react';

import ShareButton from './ShareButton';

/** Share: canonical SLUGGED url; clipboard fallback shows "Link copied". */
describe('ShareButton', () => {
  const writeText = jest.fn().mockResolvedValue(undefined);

  beforeEach(() => {
    writeText.mockClear();
    Object.assign(navigator, { clipboard: { writeText } });
    // jsdom has no navigator.share — the clipboard fallback path is the default.
  });

  it('copies the canonical slugged product URL and confirms', async () => {
    const { getByText } = render(<ShareButton goodsId={10008308} name='Rechargeable Sealer Mini' />);
    fireEvent.click(getByText('Share'));
    await waitFor(() => expect(getByText('Link copied')).not.toBeNull());
    expect(writeText).toHaveBeenCalledWith(expect.stringMatching(/\/product\/10008308-rechargeable-sealer-mini$/));
  });

  it('stays quiet when the clipboard rejects (fail-silent)', async () => {
    writeText.mockRejectedValueOnce(new Error('denied'));
    const { getByText, queryByText } = render(<ShareButton goodsId={1} name='X' />);
    fireEvent.click(getByText('Share'));
    await waitFor(() => expect(writeText).toHaveBeenCalled());
    expect(queryByText('Link copied')).toBeNull();
    expect(queryByText('Share')).not.toBeNull();
  });
});
