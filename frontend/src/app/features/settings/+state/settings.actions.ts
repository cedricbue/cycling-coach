import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { AppSettings } from '../../../core/api/model/models';

export const SettingsActions = createActionGroup({
  source: 'Settings',
  events: {
    'Load Settings': emptyProps(),
    'Load Settings Success': props<{ appSettings: AppSettings }>(),
    'Load Settings Failure': props<{ error: string }>(),
  },
});
