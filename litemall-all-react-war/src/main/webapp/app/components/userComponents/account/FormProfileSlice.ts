import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface ProfileFormState {
  imagePreview: string | null;
}

const initialState: ProfileFormState = {
  imagePreview: null,
};

const profileFormSlice = createSlice({
  name: 'profileForm',
  initialState,
  reducers: {
    setImagePreview(state, action: PayloadAction<string | null>) {
      state.imagePreview = action.payload;
    },
  },
});

export const { setImagePreview } = profileFormSlice.actions;

export default profileFormSlice.reducer;
