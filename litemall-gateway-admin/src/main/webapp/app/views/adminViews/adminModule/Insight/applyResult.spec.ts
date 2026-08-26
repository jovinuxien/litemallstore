import { describe, expect, it } from '@jest/globals';

import { mutationError } from './applyResult';

// Regression guard for the SEO title page's first live use: goods-management logged a
// SUCCESSFUL retitle while the admin showed "Request failed." The call site passed the raw
// RTK-Query result to errnoMessage, which reads the envelope one level down.

describe('mutationError', () => {
  it('treats a successful envelope as success', () => {
    expect(mutationError({ data: { errno: 0, errmsg: 'success', data: { goodsId: 1, title: 'Short' } } })).toBeNull();
  });

  it('THE BUG: a raw envelope handed in un-nested must not read as success either', () => {
    // The old code passed `res` (shape {data:…}) where the envelope was expected. Guard the
    // inverse too: a bare envelope is not the shape this helper is documented to take, so it
    // must fail loudly rather than silently pass.
    expect(mutationError({ errno: 0, errmsg: 'success' })).toBe('Request failed.');
  });

  it('surfaces the server errmsg verbatim on a business refusal', () => {
    expect(mutationError({ data: { errno: 703, errmsg: 'title must be at most 127 characters' } })).toBe(
      'title must be at most 127 characters',
    );
  });

  it('falls back to a generic message when a refusal carries no errmsg', () => {
    expect(mutationError({ data: { errno: 703, errmsg: '' } })).toBe('Request failed (errno 703)');
  });

  it('reports transport failures', () => {
    expect(mutationError({ error: { status: 500, data: 'boom' } })).toBe('Request failed.');
    expect(mutationError(undefined)).toBe('Request failed.');
    expect(mutationError(null)).toBe('Request failed.');
  });
});
