import { createFeatureSelector, createSelector } from '@ngrx/store';
import { DASHBOARD_FEATURE_KEY, DashboardState } from './dashboard.reducer';

const selectDashboardState =
  createFeatureSelector<DashboardState>(DASHBOARD_FEATURE_KEY);

export const selectPmcData = createSelector(
  selectDashboardState,
  (s) => s.pmcData
);

export const selectRecentRides = createSelector(
  selectDashboardState,
  (s) => s.recentRides
);

export const selectFtpHistory = createSelector(
  selectDashboardState,
  (s) => s.ftpHistory
);

export const selectDashboardLoading = createSelector(
  selectDashboardState,
  (s) => s.loading
);

export const selectDashboardError = createSelector(
  selectDashboardState,
  (s) => s.error
);

/** Latest (today's) PMC snapshot — last entry in the sorted array */
export const selectLatestPmc = createSelector(selectPmcData, (data) =>
  data.length > 0 ? data[data.length - 1] : null
);

/** Latest FTP value */
export const selectCurrentFtp = createSelector(selectFtpHistory, (history) => {
  if (!history.length) return null;
  return [...history].sort((a, b) =>
    (b.date ?? '').localeCompare(a.date ?? '')
  )[0].ftpValue ?? null;
});

export const selectAppSettings = createSelector(
  selectDashboardState,
  (s) => s.appSettings
);

export const selectRecommendation = createSelector(
  selectDashboardState,
  (s) => s.recommendation
);

export const selectRecommendationLoading = createSelector(
  selectDashboardState,
  (s) => s.recommendationLoading
);

export const selectRecommendationError = createSelector(
  selectDashboardState,
  (s) => s.recommendationError
);

/** FTP in W/kg — null if either weight or FTP is unknown */
export const selectFtpPerKg = createSelector(
  selectCurrentFtp,
  selectAppSettings,
  (ftp, settings) => {
    if (!ftp || !settings?.weightKg) return null;
    return ftp / settings.weightKg;
  }
);
