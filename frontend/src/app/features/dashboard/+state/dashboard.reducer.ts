import { createReducer, on } from '@ngrx/store';
import { PmcDataPoint, ActivitySummary, FtpEntry, AppSettings } from '../../../core/api/model/models';
import { DashboardActions } from './dashboard.actions';

export const DASHBOARD_FEATURE_KEY = 'dashboard';

export interface DashboardState {
  pmcData: PmcDataPoint[];
  recentRides: ActivitySummary[];
  ftpHistory: FtpEntry[];
  appSettings: AppSettings | null;
  loading: boolean;
  error: string | null;
}

const initialState: DashboardState = {
  pmcData: [],
  recentRides: [],
  ftpHistory: [],
  appSettings: null,
  loading: false,
  error: null,
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
  }))
);

