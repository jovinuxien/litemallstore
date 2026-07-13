/**
 * Customer auth + account self-service edge (`/auth/*`, NOT `/srv`). The edge
 * AuthController issues the customer JWT (issuer `litemall-customer`) and owns
 * register / password change + reset / profile / me (Wave 4 Task A).
 *
 * These use bare fetch (same-origin) because the auth responses ARE the
 * {errno,errmsg,data} envelope, not an unwrapped body — mirroring
 * customerAuthSlice.loginCustomerThunk. Authenticated calls (me/profile/reset)
 * attach the Bearer token from sessionStorage.
 *
 * Errno contract (docs/handoff-auth-account.md):
 *   700 wrong old password · 701 email reset disabled · 703 invalid/expired
 *   reset token · 704 username taken · 705 mobile taken · 402 policy violation
 *   · 501 not logged in.
 */
export interface RegisterBody {
  username: string;
  password: string;
  nickname?: string;
  email?: string;
  mobile?: string;
}

export interface AccountInfo {
  username?: string;
  nickName?: string;
  avatarUrl?: string;
  email?: string | null;
  mobile?: string;
  gender?: number;
  birthday?: string | null;
}

export interface ProfileBody {
  nickname?: string;
  email?: string;
  mobile?: string;
  avatar?: string;
  gender?: number | string;
  birthday?: string;
}

export type Envelope<T> = { errno: number; errmsg: string; data: T };

function authHeaders(): Record<string, string> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const token = sessionStorage.getItem('customerToken');
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

async function postEnvelope<T>(url: string, body: unknown): Promise<Envelope<T>> {
  const res = await fetch(url, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(body),
  });
  return res.json();
}

async function getEnvelope<T>(url: string): Promise<Envelope<T>> {
  const res = await fetch(url, { headers: authHeaders() });
  return res.json();
}

export const authApi = {
  login: (body: { username: string; password: string }) =>
    postEnvelope<{ token: string; refreshToken: string; userInfo: AccountInfo }>('/auth/login', body),
  register: (body: RegisterBody) =>
    postEnvelope<{ token: string; refreshToken: string; userInfo: AccountInfo }>('/auth/register', body),

  /** Authenticated password change. Wrong old password → errno 700. */
  reset: (body: { oldPassword: string; newPassword: string }) => postEnvelope<null>('/auth/reset', body),
  /** Forgot-password request. 701 when the email flow is disabled; errno 0 otherwise (anti-enumeration). */
  resetRequest: (body: { email: string }) => postEnvelope<null>('/auth/reset/request', body),
  /** Forgot-password confirm. 703 on invalid/expired/used token. */
  resetConfirm: (body: { token: string; newPassword: string }) => postEnvelope<null>('/auth/reset/confirm', body),

  /** Current account info (replaces the dead /srv/user/index stub). 501 when not logged in. */
  me: () => getEnvelope<AccountInfo>('/auth/me'),
  /** Partial profile update; returns the refreshed account info. */
  profile: (body: ProfileBody) => postEnvelope<AccountInfo>('/auth/profile', body),
};
