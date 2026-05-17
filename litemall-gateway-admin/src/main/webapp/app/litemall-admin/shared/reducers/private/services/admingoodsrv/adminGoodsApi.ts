import { IBrandData } from 'app/shared/model/brand.model';
import { CategoryData } from 'app/shared/model/category/category.models';
import { IGood } from 'app/shared/model/product/product.model';
import axios, { AxiosError, AxiosRequestConfig } from 'axios';

interface AdminGoodsResult {
  total: number | null;
  pages: number | null;
  limit: number | null;
  page: number | null;
  list: IGood[];
}

interface AdminGoodsCatAndBrandResult {
  catList: CategoryData[];
  brandList: IBrandData[];
}

const axiosBaseQuery =
  ({ baseUrl }: { baseUrl: string } = { baseUrl: '' }) =>
  async ({ url, method, data, params }: AxiosRequestConfig) => {
    try {
      const adminToken = sessionStorage.getItem('adminToken');
      const headers = adminToken ? { 'X-Litemall-Admin-Token': adminToken } : {};
      const result = await axios({
        url: baseUrl + url,
        method,
        data,
        params,
        headers,
      });
      return { data: result.data };
    } catch (axiosError) {
      const err = axiosError as AxiosError;
      return {
        error: {
          status: err.response?.status,
          data: err.response?.data || err.message,
        },
      };
    }
  };

/* export const adminGoodsApi = createApi({
  reducerPath: 'adminGoodsApi',
  baseQuery: axiosBaseQuery({ baseUrl: ADMIN_URL_CONTEXT }),
  endpoints: builder => ({
    getAdminGoodsList: builder.query<ApiResult<AdminGoodsResult>, { limit: number; page: number; sort: string; order: 'desc' | 'asc' }>({
      query: params => ({
        url: '/goods/list',
        method: 'GET',
        params,
      }),
    }),
    getAdminGoodsCatAndBrand: builder.query<ApiResult<AdminGoodsCatAndBrandResult>, void>({
      query: () => ({ url: '/goods/catAndBrand', method: 'GET' }),
    }),
  }),
}); */

//export const { useGetAdminGoodsListQuery, useGetAdminGoodsCatAndBrandQuery } = adminGoodsApi;
