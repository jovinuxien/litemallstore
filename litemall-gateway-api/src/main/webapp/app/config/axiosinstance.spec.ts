import { baseAxios } from './axiosinstance';

/**
 * The 401 hook must only fire for an EXPIRED session (a token was stored).
 * An anonymous visitor tripping an authenticated endpoint — e.g. a public-path
 * gap at the edge, the Wave-21 combination/active regression — must fail soft
 * in place, never be bounced to /login mid-browse.
 */
describe('baseAxios 401 handling', () => {
  // Axios keeps registered interceptors on an internal handlers list; the
  // rejection half of the pair is the handler under test.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const rejected: (e: unknown) => Promise<never> = (baseAxios.interceptors.response as any).handlers[0].rejected;

  const err401 = { response: { status: 401 } };
  // jsdom's Location has read-only methods — replace the whole object for the
  // suite and restore it after.
  const realLocation = window.location;
  const assignMock = jest.fn();

  beforeAll(() => {
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { pathname: '/product/10008302', assign: assignMock },
    });
  });

  afterAll(() => {
    Object.defineProperty(window, 'location', { configurable: true, value: realLocation });
  });

  beforeEach(() => {
    sessionStorage.clear();
    assignMock.mockClear();
  });

  it('does NOT redirect an anonymous visitor on 401', async () => {
    await expect(rejected(err401)).rejects.toBe(err401);
    expect(assignMock).not.toHaveBeenCalled();
  });

  it('expires a stored session and redirects to /login on 401', async () => {
    sessionStorage.setItem('customerToken', 'stale-jwt');
    sessionStorage.setItem('customerUserInfo', '{}');
    await expect(rejected(err401)).rejects.toBe(err401);
    expect(assignMock).toHaveBeenCalledWith('/login');
    expect(sessionStorage.getItem('customerToken')).toBeNull();
    expect(sessionStorage.getItem('customerUserInfo')).toBeNull();
  });

  it('leaves non-401 failures alone', async () => {
    const err500 = { response: { status: 500 } };
    await expect(rejected(err500)).rejects.toBe(err500);
    expect(assignMock).not.toHaveBeenCalled();
  });
});
