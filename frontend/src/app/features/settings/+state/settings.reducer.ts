import { createReducer, on } from '@ngrx/store';
import { AppSettings } from '../../../core/api/model/models';
import { SettingsActions } from './settings.actions';

export const SETTINGS_FEATURE_KEY = 'settings';

export interface SettingsState {
  appSettings: AppSettings | null;
  loading: boolean;
  error: string | null;
}

const initialState: SettingsState = {
  appSettings: null,
  loading: false,
  error: null,
};

export const settingsReducer = createReducer(
  initialState,
  on(SettingsActions.loadSettings, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(SettingsActions.loadSettingsSuccess, (state, { appSettings }) => ({
    ...state,
    appSettings,
    loading: false,
  })),
  on(SettingsActions.loadSettingsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  }))
);
