import { createReducer, on } from '@ngrx/store';
import { SyncActions } from './sync.actions';

export const SYNC_FEATURE_KEY = 'sync';

export interface SyncState {
  syncing: boolean;
  lastError: string | null;
}

const initialState: SyncState = {
  syncing: false,
  lastError: null,
};

export const syncReducer = createReducer(
  initialState,
  on(SyncActions.triggerSync, (state) => ({
    ...state,
    syncing: true,
    lastError: null,
  })),
  on(SyncActions.syncEnqueued, (state) => ({
    ...state,
    syncing: false,
  })),
  on(SyncActions.syncFailed, (state, { error }) => ({
    ...state,
    syncing: false,
    lastError: error,
  }))
);
