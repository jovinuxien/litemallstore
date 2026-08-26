import { errnoMessage } from 'app/views/adminViews/adminModule/_shared/crudUi';

/**
 * Reads the result of an RTK-Query mutation that was NOT unwrapped.
 *
 * <p>Such a call resolves to `{data}` on success and `{error}` on transport failure — the
 * envelope is one level down. Passing the result straight to {@link errnoMessage} finds no
 * numeric `errno` on it and so returns "Request failed." for EVERY call, including the ones
 * the server accepted. That is exactly what the SEO title page did on its first live use:
 * the backend logged a successful retitle and the admin still saw a failure.
 *
 * <p>Every other admin view writes `'data' in res ? errnoMessage(res.data) : '…'` inline.
 * This exists so the SEO page's version is covered by a test rather than by eyeballing.
 *
 * @returns an error message to show, or null when the mutation genuinely succeeded
 */
export const mutationError = (res: unknown): string | null => {
  if (res && typeof res === 'object' && 'data' in res) {
    return errnoMessage((res as { data: unknown }).data);
  }
  return 'Request failed.';
};
