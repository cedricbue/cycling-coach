import { syncReducer, SyncState } from './sync.reducer';
import { SyncActions } from './sync.actions';

describe('Sync Reducer', () => {
  const initialState: SyncState = {
    syncing: false,
    lastError: null,
  };

  it('should return initial state', () => {
    const result = syncReducer(undefined, { type: 'noop' } as any);
    expect(result).toEqual(initialState);
  });

  it('should set syncing to true on triggerSync', () => {
    const result = syncReducer(initialState, SyncActions.triggerSync());
    expect(result.syncing).toBe(true);
    expect(result.lastError).toBeNull();
  });

  it('should set syncing to false on syncEnqueued', () => {
    const syncingState = { ...initialState, syncing: true };
    const result = syncReducer(syncingState, SyncActions.syncEnqueued());
    expect(result.syncing).toBe(false);
  });

  it('should set error on syncFailed', () => {
    const syncingState = { ...initialState, syncing: true };
    const result = syncReducer(
      syncingState,
      SyncActions.syncFailed({ error: 'Network error' })
    );
    expect(result.syncing).toBe(false);
    expect(result.lastError).toBe('Network error');
  });

  it('should clear error on new triggerSync', () => {
    const errorState = { ...initialState, lastError: 'previous error' };
    const result = syncReducer(errorState, SyncActions.triggerSync());
    expect(result.lastError).toBeNull();
  });
});
