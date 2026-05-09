import { createFeatureSelector, createSelector } from '@ngrx/store';
import { RIDES_FEATURE_KEY, RidesState } from './rides.reducer';

const selectRidesState = createFeatureSelector<RidesState>(RIDES_FEATURE_KEY);

export const selectRidesList = createSelector(selectRidesState, (s) => s.list);
export const selectRidesTotalElements = createSelector(selectRidesState, (s) => s.totalElements);
export const selectRidesCurrentPage = createSelector(selectRidesState, (s) => s.currentPage);
export const selectRidesPageSize = createSelector(selectRidesState, (s) => s.pageSize);
export const selectRidesListLoading = createSelector(selectRidesState, (s) => s.listLoading);
export const selectRidesListError = createSelector(selectRidesState, (s) => s.listError);

export const selectRideDetail = createSelector(selectRidesState, (s) => s.detail);
export const selectRideDetailLoading = createSelector(selectRidesState, (s) => s.detailLoading);
export const selectRideDetailError = createSelector(selectRidesState, (s) => s.detailError);
