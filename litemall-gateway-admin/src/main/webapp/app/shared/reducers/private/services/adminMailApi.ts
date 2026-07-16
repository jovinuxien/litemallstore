import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';
import { fromServerDateTime } from 'app/shared/util/server-datetime';

// RTK Query client for the Wave-6 customer-mail outbox admin surface, served
// by litemall-order under /srv/private/admin/mail (rides the admin-order
// gateway route; machine-token relay + X-User-* downstream).
//
// Contract per litemall-order docs/handoff-mail-outbox-admin.md (normative):
// - GET  /list?status=&page=&limit= → legacy envelope, data =
//   {list,total,page,limit,pages}; rows carry recipient, subject, body,
//   templateKey, status pending|sent|failed, attempts (cap 5), sendAt,
//   lastError, add/updateTime (ISO LocalDateTime strings). Newest first;
//   limit capped at 100; bad status → errno 402.
// - POST /{id}/resend → guarded failed→pending reset (attempts zeroed,
//   send_at=now); non-failed/missing row → HTTP 422 {errno:422, errmsg}.

export type MailStatus = 'pending' | 'sent' | 'failed';

export const MAIL_STATUSES: MailStatus[] = ['pending', 'sent', 'failed'];

export interface IMailOutbox {
  id?: number;
  recipient?: string;
  subject?: string;
  templateKey?: string;
  status?: MailStatus;
  attempts?: number;
  sendAt?: string;
  lastError?: string;
  addTime?: string;
  updateTime?: string;
}

export interface MailListParams {
  page: number;
  limit: number;
  status?: MailStatus | '';
}

export interface MailPage {
  list: IMailOutbox[];
  total?: number;
  pages?: number;
}

const clean = (params: Record<string, unknown>): Record<string, unknown> => {
  const out: Record<string, unknown> = {};
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') out[k] = v;
  });
  return out;
};

type Raw = Record<string, unknown>;
const str = (v: unknown): string | undefined => (typeof v === 'string' && v ? v : undefined);
const num = (v: unknown): number | undefined => (typeof v === 'number' ? v : undefined);

const toRow = (r: Raw): IMailOutbox => ({
  id: num(r.id),
  recipient: str(r.recipient),
  subject: str(r.subject),
  templateKey: str(r.templateKey),
  status: str(r.status)?.toLowerCase() as MailStatus | undefined,
  attempts: num(r.attempts),
  sendAt: fromServerDateTime(r.sendAt),
  lastError: str(r.lastError),
  addTime: fromServerDateTime(r.addTime),
  updateTime: fromServerDateTime(r.updateTime),
});

// Unwrap the legacy {errno,errmsg,data:{list,total,pages}} envelope, a plain
// {list,...} page, or a bare array.
const toPage = (r: unknown): MailPage => {
  if (Array.isArray(r)) return { list: r.map(x => toRow(x as Raw)) };
  const env = (r ?? {}) as Raw;
  const body = ((typeof env.errno === 'number' ? env.data : env) ?? {}) as Raw;
  if (Array.isArray(body)) return { list: (body as unknown as Raw[]).map(toRow) };
  const list = Array.isArray(body.list) ? (body.list as Raw[]) : [];
  return { list: list.map(toRow), total: num(body.total), pages: num(body.pages) };
};

export const adminMailApi = createApi({
  reducerPath: 'adminMailApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/mail',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) headers.set('Authorization', `Bearer ${token}`);
      return headers;
    },
  }),
  tagTypes: ['MailOutbox'],
  endpoints: builder => ({
    listMailOutbox: builder.query<MailPage, MailListParams>({
      query: ({ page, limit, status }) => ({ url: '/list', params: clean({ page, limit, status }) }),
      transformResponse: toPage,
      providesTags: ['MailOutbox'],
    }),
    resendMail: builder.mutation<unknown, number>({
      query: id => ({ url: `/${id}/resend`, method: 'POST' }),
      invalidatesTags: ['MailOutbox'],
    }),
  }),
});

// Normalise a resend result into an error message (null on success): accepts
// the legacy {errno,errmsg} envelope or an {success,message} operation shape.
export const mailOpMessage = (res: unknown): string | null => {
  const r = res as {
    data?: { errno?: number; errmsg?: string; success?: boolean; message?: string };
    error?: { status?: number | string; data?: { errmsg?: string; message?: string } };
  };
  if (r && 'error' in r && r.error) {
    return r.error.data?.message || r.error.data?.errmsg || `Request failed (${r.error.status ?? 'network'})`;
  }
  if (r && 'data' in r && r.data) {
    if (typeof r.data.errno === 'number') return r.data.errno === 0 ? null : r.data.errmsg || `Request failed (errno ${r.data.errno})`;
    if (typeof r.data.success === 'boolean') return r.data.success ? null : r.data.message || 'Request failed.';
    return null;
  }
  return 'Request failed.';
};

export const { useListMailOutboxQuery, useResendMailMutation } = adminMailApi;
