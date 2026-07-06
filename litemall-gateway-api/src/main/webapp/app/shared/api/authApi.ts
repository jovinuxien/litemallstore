/**
 * Customer auth edge (`/auth/*`, NOT `/srv`). The edge AuthController issues the
 * customer JWT (issuer `litemall-customer`). login/logout exist; register/reset
 * target the agreed edge contract — if absent they 404 and the UI shows the
 * error (docs/SRV-FOLLOWUPS.md, auth edge).
 *
 * These use bare fetch (same-origin) because the login/register responses ARE
 * the {errno,errmsg,data} envelope, not an unwrapped body — mirroring
 * customerAuthSlice.loginCustomerThunk.
 */
export interface RegisterBody {
  username: string;
  password: string;
  mobile?: string;
  code?: string;
}

async function postEnvelope<T>(url: string, body: unknown): Promise<{ errno: number; errmsg: string; data: T }> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  return res.json();
}

export const authApi = {
  login: (body: { username: string; password: string }) => postEnvelope<{ token: string; refreshToken: string; userInfo: unknown }>('/auth/login', body),
  // TODO(/srv follow-up: auth edge) — register/reset endpoints.
  register: (body: RegisterBody) => postEnvelope<{ token: string; refreshToken: string; userInfo: unknown }>('/auth/register', body),
  reset: (body: { password: string; mobile: string; code: string }) => postEnvelope<null>('/auth/reset', body),
};
