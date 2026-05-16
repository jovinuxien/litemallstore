import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

export const TopLevelApiSlice = createApi({
  reducerPath: 'topLevelApi',
  baseQuery: fetchBaseQuery({ baseUrl: 'http://localhost:9000/wx' }),
  endpoints: builder => ({
    getHomeIndex: builder.query({
      query: () => 'home/index',
    }),
    getCatalogIndex: builder.query({
      query: () => 'catalog/index',
    }),
    getCatalogCurrent: builder.query({
      query: () => 'catalog/current',
    }),
    getGoodsList: builder.query({
      query: () => 'goods/list',
    }),
    getGoodsCategory: builder.query({
      query: () => 'goods/category',
    }),
  }),
});

export const { useGetHomeIndexQuery, useGetCatalogIndexQuery, useGetCatalogCurrentQuery, useGetGoodsListQuery, useGetGoodsCategoryQuery } = TopLevelApiSlice;
