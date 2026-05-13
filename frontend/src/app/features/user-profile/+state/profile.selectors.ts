import { createFeatureSelector, createSelector } from '@ngrx/store';
import { PROFILE_FEATURE_KEY, ProfileState } from './profile.reducer';

const selectProfileState =
  createFeatureSelector<ProfileState>(PROFILE_FEATURE_KEY);

export const selectProfileLoading = createSelector(
  selectProfileState,
  (s) => s.loading
);

export const selectFtpHistory = createSelector(
  selectProfileState,
  (s) => [...s.ftpHistory].sort((a, b) =>
    (b.date ?? '').localeCompare(a.date ?? '')
  )
);

export const selectWeightHistory = createSelector(
  selectProfileState,
  (s) => [...s.weightHistory].sort((a, b) =>
    (b.date ?? '').localeCompare(a.date ?? '')
  )
);
