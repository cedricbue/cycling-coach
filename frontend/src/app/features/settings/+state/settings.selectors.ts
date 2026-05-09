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

export const selectFtpHistory = createSelector(
  selectSettingsState,
  (s) => [...s.ftpHistory].sort((a, b) =>
    (b.date ?? '').localeCompare(a.date ?? '')
  )
);

export const selectSettingsLoading = createSelector(
  selectSettingsState,
  (s) => s.loading
);

export const selectCurrentFtp = createSelector(
  selectFtpHistory,
  (history) => (history.length > 0 ? history[0].ftpValue ?? null : null)
);
