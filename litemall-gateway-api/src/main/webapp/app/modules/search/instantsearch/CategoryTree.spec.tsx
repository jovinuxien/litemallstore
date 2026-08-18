import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

/**
 * Wave 26 — the department rail lists a category's children with their product
 * counts, straight from `/srv/search/category/{id}`. On the narrowed catalogue
 * some of those children measure zero (Arts, Crafts & Sewing still carries a
 * "Cross-Stitch" child with nothing in it), and a link to an empty page is the
 * failure this wave exists to remove. A child whose count the backend did NOT
 * measure is a different thing and must survive.
 */
jest.mock('app/config/axiosinstance', () => ({
  baseAxios: { get: jest.fn(), post: jest.fn() },
}));

import { baseAxios } from 'app/config/axiosinstance';
import CategoryTree from './CategoryTree';

const mockGet = baseAxios.get as jest.Mock;

// The live shape of /srv/search/category/1036152 (2026-08-18), zero-count child included.
const detail = {
  category: { id: 1036152, name: 'Arts, Crafts & Sewing' },
  breadcrumb: [
    { id: 1036143, name: 'Home, Garden & Furniture' },
    { id: 1036152, name: 'Arts, Crafts & Sewing' },
  ],
  subcategories: [
    { id: 1036153, name: 'Decor Paintings', count: 85 },
    { id: 1036154, name: 'Cross-Stitch', count: 0 },
    { id: 1036155, name: 'Ribbons', count: 3 },
    { id: 1036156, name: 'Unmeasured' },
  ],
};

const renderTree = (payload: unknown = detail) => {
  mockGet.mockImplementation(() => Promise.resolve({ data: { errno: 0, data: payload } }));
  return render(
    <MemoryRouter>
      <CategoryTree categoryId="1036152" />
    </MemoryRouter>
  );
};

beforeEach(() => mockGet.mockReset());

describe('CategoryTree children', () => {
  it('drops a child measured at zero products', async () => {
    renderTree();
    expect(await screen.findByText('Decor Paintings')).toBeTruthy();
    expect(screen.queryByText('Cross-Stitch')).toBeNull();
  });

  it('keeps children with products, and their counts', async () => {
    renderTree();
    expect(await screen.findByText('Ribbons')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
  });

  it('keeps a child the backend never counted — absent is not empty', async () => {
    renderTree();
    expect(await screen.findByText('Unmeasured')).toBeTruthy();
  });

  it('says "No subcategories" rather than listing empty ones', async () => {
    renderTree({ ...detail, subcategories: [{ id: 9, name: 'Gone', count: 0 }] });
    expect(await screen.findByText('No subcategories')).toBeTruthy();
    expect(screen.queryByText('Gone')).toBeNull();
  });

  it('still renders the breadcrumb of the department itself', async () => {
    renderTree();
    await waitFor(() => expect(screen.getByText('Home, Garden & Furniture')).toBeTruthy());
  });
});
