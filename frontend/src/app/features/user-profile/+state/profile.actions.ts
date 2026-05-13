import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { FtpEntry, WeightEntry } from '../../../core/api/model/models';

export const ProfileActions = createActionGroup({
  source: 'Profile',
  events: {
    'Load Profile': emptyProps(),
    'Load Profile Success': props<{
      ftpHistory: FtpEntry[];
      weightHistory: WeightEntry[];
    }>(),
    'Load Profile Failure': props<{ error: string }>(),
  },
});
