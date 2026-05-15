import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { PmcDataPoint, RideSummary, FtpEntry, AppSettings, DailyRecommendation } from '../../../core/api/model/models';

export const DashboardActions = createActionGroup({
  source: 'Dashboard',
  events: {
    'Load Dashboard': emptyProps(),
    'Load Dashboard Success': props<{
      pmcData: PmcDataPoint[];
      recentRides: RideSummary[];
      ftpHistory: FtpEntry[];
      appSettings: AppSettings;
    }>(),
    'Load Dashboard Failure': props<{ error: string }>(),
    'Load Recommendation': props<{ regenerate: boolean }>(),
    'Load Recommendation Success': props<{ recommendation: DailyRecommendation }>(),
    'Load Recommendation Failure': props<{ error: string }>(),
    'Regenerate Recommendation': emptyProps(),
  },
});
