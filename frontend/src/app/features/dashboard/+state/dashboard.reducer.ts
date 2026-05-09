import { createReducer, on } from '@ngrx/store';
import { PmcDataPoint, ActivitySummary, FtpEntry } from '../../../core/api/model/models';
import { DashboardActions } from './dashboard.actions';

export const DASHBOARD_FEATURE_KEY = 'dashboard';

export interface DashboardState {
  pmcData: PmcDataPoint[];
  recentRides: ActivitySummary[];
  ftpHistory: FtpEntry[];
  loading: boolean;
  error: string | null;
}

const initialState: DashboardState = {
  pmcData: [],
  recentRides: [],
  ftpHistory: [],
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
  on(DashboardActions.loadDashboardSuccess, (state, { pmcData, recentRides, ftpHistory }) => ({
    ...state,
    pmcData,
    recentRides,
    ftpHistory,
    loading: false,
  })),
  on(DashboardActions.loadDashboardFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  }))
);
