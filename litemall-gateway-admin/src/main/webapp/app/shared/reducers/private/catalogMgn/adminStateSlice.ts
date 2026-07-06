import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import axios from 'axios';

// Dashboard statistics, sourced from goods-management (which hosts the ported
// litemall-db StatService/dashboard endpoints) through the gateway as an
// authenticated admin (Bearer admin JWT; MachineTokenRelayFilter adds the
// machine token downstream, the edge gates '/srv/private/admin/**' to
// ROLE_ADMIN). No mock data and no stat computation here — the numbers come
// from the backend. Both fetches degrade gracefully to an "unavailable" banner.

const ORDER_STATS_URL = '/srv/private/admin/stat/order';
const DASHBOARD_URL = '/srv/private/admin/dashboard';

export interface RowOrder {
  day: string;
  orders: number;
  customers: number;
  amount: number;
  pcr?: number;
}

export interface OrderStatsResult {
  rows: RowOrder[];
  totals: { orders: number; customers: number; amount: number };
}

export interface DashboardTotals {
  userTotal: number;
  goodsTotal: number;
  productTotal: number;
  orderTotal: number;
}

interface OrderStatsState {
  rows: RowOrder[];
  totals: { orders: number; customers: number; amount: number };
  dashboard: DashboardTotals | null;
  loading: boolean;
  unavailable: boolean;
  errorMessage: string | null;
}

const numeric = (value: unknown): number => {
  if (typeof value === 'number') return value;
  if (value && typeof (value as { amount?: unknown }).amount === 'number') {
    return (value as { amount: number }).amount;
  }
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
};

const toRow = (r: Record<string, unknown>): RowOrder => ({
  day: String(r.day ?? r.date ?? ''),
  orders: numeric(r.orders),
  customers: numeric(r.customers),
  amount: numeric(r.amount),
  pcr: r.pcr != null ? numeric(r.pcr) : undefined,
});

export const fetchOrderStats = createAsyncThunk<OrderStatsResult, void, { rejectValue: { unavailable: boolean; message: string } }>(
  'adminState/fetchOrderStats',
  async (_, thunkApi) => {
    try {
      const response = await axios.get(ORDER_STATS_URL);
      const body = response.data;
      // The litemall envelope can signal failure via a non-zero `errno` even on
      // HTTP 200 (e.g. 501 "business not supported" before the order service
      // implements this endpoint). Treat that as unavailable, not empty data.
      if (body && typeof body.errno === 'number' && body.errno !== 0) {
        return thunkApi.rejectWithValue({ unavailable: true, message: 'Order statistics are not available yet.' });
      }
      // Accept either { errno, data: { rows | columns/rows | [] } } or a raw array.
      const payload = body?.data ?? body;
      const rawRows: Record<string, unknown>[] = Array.isArray(payload) ? payload : payload?.rows ?? [];
      const rows = rawRows.map(toRow);
      const totals = rows.reduce(
        (acc, r) => ({ orders: acc.orders + r.orders, customers: acc.customers + r.customers, amount: acc.amount + r.amount }),
        { orders: 0, customers: 0, amount: 0 }
      );
      return { rows, totals };
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      // Treat a missing endpoint (404) or unreachable order service as
      // "unavailable" so the dashboard degrades instead of erroring hard.
      const unavailable = status === 404 || status == null || status === 503 || status === 502;
      return thunkApi.rejectWithValue({
        unavailable,
        message: unavailable ? 'Order statistics are not available yet.' : `Failed to load order statistics (${status}).`,
      });
    }
  }
);

export const fetchDashboardTotals = createAsyncThunk<DashboardTotals, void, { rejectValue: { message: string } }>(
  'adminState/fetchDashboardTotals',
  async (_, thunkApi) => {
    try {
      const response = await axios.get(DASHBOARD_URL);
      const body = response.data;
      if (body && typeof body.errno === 'number' && body.errno !== 0) {
        return thunkApi.rejectWithValue({ message: body.errmsg || 'Dashboard totals are not available.' });
      }
      const d = body?.data ?? {};
      return {
        userTotal: numeric(d.userTotal),
        goodsTotal: numeric(d.goodsTotal),
        productTotal: numeric(d.productTotal),
        orderTotal: numeric(d.orderTotal),
      };
    } catch (error) {
      return thunkApi.rejectWithValue({ message: 'Dashboard totals are not available.' });
    }
  }
);

const initialState: OrderStatsState = {
  rows: [],
  totals: { orders: 0, customers: 0, amount: 0 },
  dashboard: null,
  loading: false,
  unavailable: false,
  errorMessage: null,
};

const adminStateSlice = createSlice({
  name: 'adminState',
  initialState,
  reducers: {},
  extraReducers: builder => {
    builder
      .addCase(fetchOrderStats.pending, state => {
        state.loading = true;
        state.unavailable = false;
        state.errorMessage = null;
      })
      .addCase(fetchOrderStats.fulfilled, (state, action) => {
        state.loading = false;
        state.rows = action.payload.rows;
        state.totals = action.payload.totals;
      })
      .addCase(fetchOrderStats.rejected, (state, action) => {
        state.loading = false;
        state.rows = [];
        state.totals = { orders: 0, customers: 0, amount: 0 };
        state.unavailable = action.payload?.unavailable ?? true;
        state.errorMessage = action.payload?.message || action.error.message || 'Failed to load order statistics.';
      })
      .addCase(fetchDashboardTotals.fulfilled, (state, action) => {
        state.dashboard = action.payload;
      })
      .addCase(fetchDashboardTotals.rejected, state => {
        state.dashboard = null;
      });
  },
});

export default adminStateSlice.reducer;
