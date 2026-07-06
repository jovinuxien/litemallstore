import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query API for the admin statistics pages, served by goods-management
// (ported litemall-db StatService) through the gateway under
// '/srv/private/admin/stat' as an authenticated admin.

export type StatKind = 'user' | 'order' | 'goods';

export interface StatVo {
  columns: string[];
  rows: Record<string, unknown>[];
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data: T;
}

export const adminStatApi = createApi({
  reducerPath: 'adminStatApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin/stat',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  endpoints: builder => ({
    getStat: builder.query<StatVo, StatKind>({
      query: kind => ({ url: `/${kind}` }),
      transformResponse: (response: ApiEnvelope<StatVo>) => response?.data ?? { columns: [], rows: [] },
    }),
  }),
});

export const { useGetStatQuery } = adminStatApi;
