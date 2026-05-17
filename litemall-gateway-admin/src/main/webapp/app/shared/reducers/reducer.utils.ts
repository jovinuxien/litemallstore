import { ActionReducerMapBuilder, AnyAction, AsyncThunk, createSlice, SerializedError, SliceCaseReducers, ValidateSliceCaseReducers } from '@reduxjs/toolkit';
import { AxiosError, isAxiosError } from 'axios';

export type IQueryParams = { query?: string; page?: number; size?: number; sort?: string };

type GenericAsyncThunk = AsyncThunk<unknown, unknown, unknown>;

export type PendingAction = ReturnType<GenericAsyncThunk['pending']>;
export type RejectedAction = ReturnType<GenericAsyncThunk['rejected']>;
export type FulfilledAction = ReturnType<GenericAsyncThunk['fulfilled']>;

export function isRejectedAction(action: AnyAction) {
  return action.type.endsWith('rejected');
}

export function isPendingAction(action: AnyAction) {
  return action.type.endsWith('pending');
}

export function isFulfilledAction(action: AnyAction) {
  return action.type.endsWith('fulfilled');
}

const commonErrorProperties: Array<keyof SerializedError> = ['name', 'message', 'stack', 'code'];

/**
 * serialize function used for async action errors,
 * since the default function from Redux Toolkit strips useful info from axios errors
 */
export const serializeAxiosError = (value: unknown): AxiosError | SerializedError => {
  if (typeof value === 'object' && value !== null) {
    if (isAxiosError(value)) {
      return value;
    } else {
      const simpleError: SerializedError = {};
      for (const property of commonErrorProperties) {
        if (typeof value[property] === 'string') {
          simpleError[property] = value[property];
        }
      }

      return simpleError;
    }
  }
  return { message: String(value) };
};

export interface EntityState<T> {
  loading: boolean;
  errorMessage: string | null;
  entities: ReadonlyArray<T>;
  entity: T;
  links?: unknown;
  updating: boolean;
  totalItems?: number;
  updateSuccess: boolean;
}

/**
 * A wrapper on top of createSlice from Redux Toolkit to extract
 * common reducers and matchers used by entities
 */
export const createEntitySlice = <T, Reducers extends SliceCaseReducers<EntityState<T>>>({
  name = '',
  initialState,
  reducers,
  extraReducers,
  skipRejectionHandling,
}: {
  name: string;
  initialState: EntityState<T>;
  reducers?: ValidateSliceCaseReducers<EntityState<T>, Reducers>;
  extraReducers?: (builder: ActionReducerMapBuilder<EntityState<T>>) => void;
  skipRejectionHandling?: boolean;
}) => {
  return createSlice({
    name,
    initialState,
    reducers: {
      /**
       * Reset  the entity state to initial state
       */
      reset() {
        return initialState;
      },
      ...reducers,
    },
    extraReducers(builder) {
      extraReducers(builder);
      /*
       * Common rejection logic is handled here.
       * If you want to add your own rejcetion logic, pass `skipRejectionHandling: true`
       * while calling `createEntitySlice`
       * */
      if (skipRejectionHandling) {
        builder.addMatcher(isRejectedAction, (state, action) => {
          state.loading = false;
          state.updating = false;
          state.updateSuccess = false;
          //Here i replaced action error message with action type just for it to compile
          //state.errorMessage = action.error.message;
          state.errorMessage = action.type;
        });
      }
    },
  });
};
