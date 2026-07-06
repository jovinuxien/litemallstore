import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// UI state for the admin goods inline list: pagination + sort controls only.
// The list/detail data itself is fetched via adminGoodsApi (RTK Query).
export interface AdminGoodsUiState {
  page: number;
  limit: number;
  sort: string;
  order: 'asc' | 'desc';
}

const initialState: AdminGoodsUiState = {
  page: 1,
  limit: 20,
  sort: 'add_time',
  order: 'desc',
};

const adminGoodsSlice = createSlice({
  name: 'adminGoods',
  initialState,
  reducers: {
    setPage: (state, action: PayloadAction<number>) => {
      state.page = action.payload;
    },
    setLimit: (state, action: PayloadAction<number>) => {
      state.limit = action.payload;
      state.page = 1;
    },
    setSort: (state, action: PayloadAction<{ sort: string; order: 'asc' | 'desc' }>) => {
      state.sort = action.payload.sort;
      state.order = action.payload.order;
      state.page = 1;
    },
  },
});

export const { setPage, setLimit, setSort } = adminGoodsSlice.actions;
export default adminGoodsSlice.reducer;
