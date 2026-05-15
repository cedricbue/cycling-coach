import { createReducer, on } from '@ngrx/store';
import { PmcDataPoint, RideSummary, FtpEntry, AppSettings, DailyRecommendation } from '../../../core/api/model/models';
import { DashboardActions } from './dashboard.actions';

export const DASHBOARD_FEATURE_KEY = 'dashboard';

export interface DashboardState {
  pmcData: PmcDataPoint[];
  recentRides: RideSummary[];
  ftpHistory: FtpEntry[];
  appSettings: AppSettings | null;
  loading: boolean;
  error: string | null;
  recommendation: DailyRecommendation | null;
  recommendationLoading: boolean;
  recommendationError: string | null;
}

const initialState: DashboardState = {
  pmcData: [],
  recentRides: [],
  ftpHistory: [],
  appSettings: null,
  loading: false,
  error: null,
  recommendation: null,
  recommendationLoading: false,
  recommendationError: null,
};

export const dashboardReducer = createReducer(
  initialState,
  on(DashboardActions.loadDashboard, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(DashboardActions.loadDashboardSuccess, (state, { pmcData, recentRides, ftpHistory, appSettings }) => ({
    ...state,
    pmcData,
    recentRides,
    ftpHistory,
    appSettings,
    loading: false,
  })),
  on(DashboardActions.loadDashboardFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),
  on(DashboardActions.loadRecommendation, DashboardActions.regenerateRecommendation, (state) => ({
    ...state,
    recommendationLoading: true,
    recommendationError: null,
  })),
  on(DashboardActions.loadRecommendationSuccess, (state, { recommendation }) => ({
    ...state,
    recommendation,
    recommendationLoading: false,
  })),
  on(DashboardActions.loadRecommendationFailure, (state, { error }) => ({
    ...state,
    recommendationLoading: false,
    recommendationError: error,
  }))
);

