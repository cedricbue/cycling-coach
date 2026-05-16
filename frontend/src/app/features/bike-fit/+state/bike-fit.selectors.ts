import { createFeatureSelector, createSelector } from '@ngrx/store';
import { BIKE_FIT_FEATURE_KEY, BikeFitState } from './bike-fit.reducer';

const selectBikeFitState = createFeatureSelector<BikeFitState>(BIKE_FIT_FEATURE_KEY);

export const selectAnalyses = createSelector(selectBikeFitState, (s) => s.analyses);
export const selectListLoading = createSelector(selectBikeFitState, (s) => s.listLoading);
export const selectUploading = createSelector(selectBikeFitState, (s) => s.uploading);
export const selectUploadError = createSelector(selectBikeFitState, (s) => s.uploadError);
export const selectDetail = createSelector(selectBikeFitState, (s) => s.detail);
export const selectDetailLoading = createSelector(selectBikeFitState, (s) => s.detailLoading);
export const selectDetailError = createSelector(selectBikeFitState, (s) => s.detailError);
