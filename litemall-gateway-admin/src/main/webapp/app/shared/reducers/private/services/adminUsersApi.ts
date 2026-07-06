import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

import { getAdminToken } from 'app/shared/reducers/admin-auth';

// RTK Query API for admin user (customer) + address management, served by
// goods-management (ported litemall-admin-api controllers) through the gateway
// under '/srv/private/admin/{user,address}' as an authenticated admin.

export interface IAdminUser {
  id: number;
  username?: string;
  nickname?: string;
  mobile?: string;
  avatar?: string;
  gender?: number; // 0 unknown, 1 male, 2 female
  userLevel?: number; // 0 normal, 1 vip, 2 gold
  status?: number; // 0 enabled, 1 disabled, 2 closed
  lastLoginTime?: string;
  addTime?: string;
}

export interface IAdminAddress {
  id: number;
  userId?: number;
  name?: string;
  tel?: string;
  province?: string;
  city?: string;
  county?: string;
  addressDetail?: string;
  postalCode?: string;
  isDefault?: boolean;
  addTime?: string;
}

export interface AdminListResponse<T> {
  total: number;
  pages: number;
  limit: number;
  page: number;
  list: T[];
}

export interface UserListParams {
  username?: string;
  mobile?: string;
  page: number;
  limit: number;
  sort?: string;
  order?: 'asc' | 'desc';
}

export interface AddressListParams {
  userId?: number | string;
  name?: string;
  page: number;
  limit: number;
}

interface ApiEnvelope<T> {
  errno: number;
  errmsg: string;
  data: T;
}

const EMPTY_LIST = { total: 0, pages: 0, limit: 0, page: 1, list: [] };

export const adminUsersApi = createApi({
  reducerPath: 'adminUsersApi',
  baseQuery: fetchBaseQuery({
    baseUrl: '/srv/private/admin',
    prepareHeaders: headers => {
      const token = getAdminToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  tagTypes: ['Users'],
  endpoints: builder => ({
    listUsers: builder.query<AdminListResponse<IAdminUser>, UserListParams>({
      query: params => ({ url: '/user/list', params: { ...params, username: params.username || undefined, mobile: params.mobile || undefined } }),
      transformResponse: (response: ApiEnvelope<AdminListResponse<IAdminUser>>) => response?.data ?? EMPTY_LIST,
      providesTags: ['Users'],
    }),
    updateUser: builder.mutation<ApiEnvelope<unknown>, Partial<IAdminUser> & { id: number }>({
      query: body => ({ url: '/user/update', method: 'POST', body }),
      invalidatesTags: ['Users'],
    }),
    listAddresses: builder.query<AdminListResponse<IAdminAddress>, AddressListParams>({
      query: params => ({ url: '/address/list', params: { ...params, userId: params.userId || undefined, name: params.name || undefined } }),
      transformResponse: (response: ApiEnvelope<AdminListResponse<IAdminAddress>>) => response?.data ?? EMPTY_LIST,
    }),
  }),
});

export const { useListUsersQuery, useUpdateUserMutation, useListAddressesQuery } = adminUsersApi;
