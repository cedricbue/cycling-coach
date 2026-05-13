import { createReducer, on } from '@ngrx/store';
import { FtpEntry, WeightEntry } from '../../../core/api/model/models';
import { ProfileActions } from './profile.actions';

export const PROFILE_FEATURE_KEY = 'profile';

export interface ProfileState {
  ftpHistory: FtpEntry[];
  weightHistory: WeightEntry[];
  loading: boolean;
  error: string | null;
}

const initialState: ProfileState = {
  ftpHistory: [],
  weightHistory: [],
  loading: false,
  error: null,
};

export const profileReducer = createReducer(
  initialState,
  on(ProfileActions.loadProfile, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),
  on(ProfileActions.loadProfileSuccess, (state, { ftpHistory, weightHistory }) => ({
    ...state,
    ftpHistory,
    weightHistory,
    loading: false,
  })),
  on(ProfileActions.loadProfileFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  }))
);
