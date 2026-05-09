import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { PmcDataPoint, ActivitySummary, FtpEntry, AppSettings } from '../../../core/api/model/models';

export const DashboardActions = createActionGroup({
  source: 'Dashboard',
  events: {
    'Load Dashboard': emptyProps(),
    'Load Dashboard Success': props<{
      pmcData: PmcDataPoint[];
      recentRides: ActivitySummary[];
      ftpHistory: FtpEntry[];
      appSettings: AppSettings;
    }>(),
    'Load Dashboard Failure': props<{ error: string }>(),
  },
});
