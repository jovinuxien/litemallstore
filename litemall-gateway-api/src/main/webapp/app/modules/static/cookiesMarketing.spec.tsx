import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

import Cookies from './Cookies';

/**
 * The cookie policy described the Meta Pixel in detail while no pixel was
 * configured in production, so it documented a tracker that never loaded.
 * The section is now config-driven, matching what CookiePreferences right
 * above it has always done ("no analytics or marketing tools configured").
 *
 * Disclosing more than we do is the harmless direction to be wrong in, which
 * is exactly why it survived so long — hence the test, on both branches.
 *
 * The site-config store is mocked rather than driven through a stubbed fetch:
 * it caches its resolved config in module state, and clearing that with
 * jest.resetModules() hands the re-imported tree a SECOND React instance, whose
 * hook dispatcher is null.
 */
// Two frozen snapshots, not a fresh object per call: useSyncExternalStore
// compares snapshots by REFERENCE, so a mock that rebuilds the object every
// read re-renders forever ("Maximum update depth exceeded").
interface ConfigSnapshot {
  loaded: boolean;
  config: { metaPixelId: string | null };
}
const WITHOUT_PIXEL: ConfigSnapshot = { loaded: true, config: { metaPixelId: null } };
const WITH_PIXEL: ConfigSnapshot = { loaded: true, config: { metaPixelId: '123456789' } };
let snapshot: ConfigSnapshot = WITHOUT_PIXEL;

jest.mock('app/shared/config/siteConfig', () => ({
  loadSiteConfig: () => Promise.resolve({}),
  subscribeSiteConfig: () => () => undefined,
  siteConfigSnapshot: () => snapshot,
}));

const renderPolicy = (): void => {
  render(
    <MemoryRouter>
      <Cookies />
    </MemoryRouter>
  );
};

describe('cookie policy marketing section', () => {
  it('stays out of the page when no pixel is configured', () => {
    snapshot = WITHOUT_PIXEL;
    renderPolicy();

    // The analytics section is unconditional, so its presence proves the page
    // rendered and the absence below is a real branch, not a failed render.
    expect(screen.getByText(/Analytics — optional/)).toBeTruthy();
    expect(screen.queryByText(/Marketing — optional/)).toBeNull();
    expect(screen.queryByText(/Meta \(Facebook\) Pixel/)).toBeNull();
  });

  it('appears once a pixel id is configured — no rebuild needed', () => {
    snapshot = WITH_PIXEL;
    renderPolicy();

    expect(screen.getByText(/Marketing — optional/)).toBeTruthy();
    expect(screen.getByText(/Meta \(Facebook\) Pixel/)).toBeTruthy();
  });

  it('always states the choice, the necessary storage and the Do Not Track stance', () => {
    snapshot = WITHOUT_PIXEL;
    renderPolicy();

    expect(screen.getByText('Your choice')).toBeTruthy();
    expect(screen.getByText('Strictly necessary')).toBeTruthy();
    expect(screen.getByText('Do Not Track')).toBeTruthy();
  });
});
