import { createActionGroup, emptyProps, props } from '@ngrx/store';

export const SyncActions = createActionGroup({
  source: 'Sync',
  events: {
    'Trigger Sync': emptyProps(),
    'Sync Enqueued': emptyProps(),
    'Sync Failed': props<{ error: string }>(),
  },
});
