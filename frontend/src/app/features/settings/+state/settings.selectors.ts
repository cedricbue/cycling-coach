import { createFeatureSelector, createSelector } from '@ngrx/store';
import { SETTINGS_FEATURE_KEY, SettingsState } from './settings.reducer';

const selectSettingsState =
  createFeatureSelector<SettingsState>(SETTINGS_FEATURE_KEY);

export const selectAppSettings = createSelector(
  selectSettingsState,
  (s) => s.appSettings
);

export const selectPowerZones = createSelector(
  selectSettingsState,
  (s) => s.appSettings?.powerZones ?? null
);

export const selectCurrentFtp = createSelector(
  selectSettingsState,
  (s) => s.appSettings?.currentFtp ?? null
);

export const selectHrZones = createSelector(
  selectSettingsState,
  (s) => s.appSettings?.hrZones ?? null
);

export const selectMaxHr = createSelector(
  selectSettingsState,
  (s) => s.appSettings?.maxHrBpm ?? null
);

export const selectSettingsLoading = createSelector(
  selectSettingsState,
  (s) => s.loading
);
