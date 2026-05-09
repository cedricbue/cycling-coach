import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { forkJoin } from 'rxjs';
import { catchError, map, of, switchMap } from 'rxjs';
import { DashboardActions } from './dashboard.actions';
import { PmcService } from '../../../core/api/api/pmc.service';
import { ActivitiesService } from '../../../core/api/api/activities.service';
import { FtpService } from '../../../core/api/api/ftp.service';
import { SettingsService } from '../../../core/api/api/settings.service';

@Injectable()
export class DashboardEffects {
  private readonly actions$ = inject(Actions);
  private readonly pmcService = inject(PmcService);
  private readonly activitiesService = inject(ActivitiesService);
  private readonly ftpService = inject(FtpService);
  private readonly settingsService = inject(SettingsService);

  readonly loadDashboard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DashboardActions.loadDashboard),
      switchMap(() => {
        const ninetyDaysAgo = new Date();
        ninetyDaysAgo.setDate(ninetyDaysAgo.getDate() - 90);
        const from = ninetyDaysAgo.toISOString().split('T')[0];

        return forkJoin({
          pmcData: this.pmcService.getPmc(from),
          recentRidesPage: this.activitiesService.getActivities(0, 10),
          ftpHistory: this.ftpService.getFtpHistory(),
          appSettings: this.settingsService.getSettings(),
        }).pipe(
          map(({ pmcData, recentRidesPage, ftpHistory, appSettings }) =>
            DashboardActions.loadDashboardSuccess({
              pmcData,
              recentRides: recentRidesPage.content ?? [],
              ftpHistory,
              appSettings,
            })
          ),
          catchError((error) =>
            of(
              DashboardActions.loadDashboardFailure({
                error: error?.message ?? 'Unknown error',
              })
            )
          )
        );
      })
    )
  );
}
