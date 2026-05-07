import { createFeatureSelector, createSelector } from '@ngrx/store';
import { SYNC_FEATURE_KEY, SyncState } from './sync.reducer';

const selectSyncState = createFeatureSelector<SyncState>(SYNC_FEATURE_KEY);

export const selectSyncing = createSelector(
  selectSyncState,
  (state) => state.syncing
);

export const selectSyncError = createSelector(
  selectSyncState,
  (state) => state.lastError
);
