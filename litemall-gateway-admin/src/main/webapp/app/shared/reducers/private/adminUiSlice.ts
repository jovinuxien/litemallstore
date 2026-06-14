import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// UI-only state for the admin layout shell, mirroring the upstream
// vue-element-admin `app` + `tagsView` Vuex modules:
//  - sidebarCollapsed: hamburger collapse state for the dark sidebar
//  - visitedTags: the open route "tags" shown in the tags-view bar
// No server data lives here; the data slices (adminGoods/adminState) and
// adminGoodsApi own that.

export interface VisitedTag {
  path: string; // full route path, e.g. /admin/goods
  title: string; // breadcrumb/tag label from the menu config
  affix?: boolean; // affixed tags (Dashboard) cannot be closed
}

interface AdminUiState {
  sidebarCollapsed: boolean;
  visitedTags: VisitedTag[];
}

const DASHBOARD_TAG: VisitedTag = { path: '/admin/dashboard', title: 'Dashboard', affix: true };

const initialState: AdminUiState = {
  sidebarCollapsed: false,
  visitedTags: [DASHBOARD_TAG],
};

const adminUiSlice = createSlice({
  name: 'adminUi',
  initialState,
  reducers: {
    toggleSidebar(state) {
      state.sidebarCollapsed = !state.sidebarCollapsed;
    },
    setSidebarCollapsed(state, action: PayloadAction<boolean>) {
      state.sidebarCollapsed = action.payload;
    },
    addVisitedTag(state, action: PayloadAction<VisitedTag>) {
      const { path } = action.payload;
      if (!state.visitedTags.some(t => t.path === path)) {
        state.visitedTags.push(action.payload);
      }
    },
    removeVisitedTag(state, action: PayloadAction<string>) {
      state.visitedTags = state.visitedTags.filter(t => t.affix || t.path !== action.payload);
    },
  },
});

export const { toggleSidebar, setSidebarCollapsed, addVisitedTag, removeVisitedTag } = adminUiSlice.actions;
export default adminUiSlice.reducer;
