import { createReducer, on } from '@ngrx/store';
import { RideDetail, RideSummary } from '../../../core/api';
import { RidesActions } from './rides.actions';

export const RIDES_FEATURE_KEY = 'rides';

export interface RidesState {
  list: RideSummary[];
  totalElements: number;
  currentPage: number;
  pageSize: number;
  listLoading: boolean;
  listError: string | null;

  detail: RideDetail | null;
  detailLoading: boolean;
  detailError: string | null;
}

const initialState: RidesState = {
  list: [],
  totalElements: 0,
  currentPage: 0,
  pageSize: 20,
  listLoading: false,
  listError: null,
  detail: null,
  detailLoading: false,
  detailError: null,
};

export const ridesReducer = createReducer(
  initialState,

  on(RidesActions.loadRides, (state, { page, size }) => ({
    ...state,
    listLoading: true,
    listError: null,
    currentPage: page,
    pageSize: size,
  })),
  on(RidesActions.loadRidesSuccess, (state, { ridePage }) => ({
    ...state,
    listLoading: false,
    list: ridePage.content ?? [],
    totalElements: ridePage.totalElements ?? 0,
  })),
  on(RidesActions.loadRidesFailure, (state, { error }) => ({
    ...state,
    listLoading: false,
    listError: error,
  })),

  on(RidesActions.loadRide, (state) => ({
    ...state,
    detailLoading: true,
    detailError: null,
    detail: null,
  })),
  on(RidesActions.loadRideSuccess, (state, { ride }) => ({
    ...state,
    detailLoading: false,
    detail: ride,
  })),
  on(RidesActions.loadRideFailure, (state, { error }) => ({
    ...state,
    detailLoading: false,
    detailError: error,
  })),
);
