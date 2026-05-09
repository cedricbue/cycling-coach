import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { AppSettings, FtpEntry } from '../../../core/api/model/models';

export const SettingsActions = createActionGroup({
  source: 'Settings',
  events: {
    'Load Settings': emptyProps(),
    'Load Settings Success': props<{
      appSettings: AppSettings;
      ftpHistory: FtpEntry[];
    }>(),
    'Load Settings Failure': props<{ error: string }>(),
  },
});
