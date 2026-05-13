import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { forkJoin } from 'rxjs';
import { catchError, map, of, switchMap } from 'rxjs';
import { DashboardActions } from './dashboard.actions';
import { PmcService } from '../../../core/api/api/pmc.service';
import { RidesService } from '../../../core/api/api/rides.service';
import { FtpService } from '../../../core/api/api/ftp.service';
import { SettingsService } from '../../../core/api/api/settings.service';

@Injectable()
export class DashboardEffects {
  private readonly actions$ = inject(Actions);
  private readonly pmcService = inject(PmcService);
  private readonly ridesService = inject(RidesService);
  private readonly ftpService = inject(FtpService);
  private readonly settingsService = inject(SettingsService);

  readonly loadDashboard$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DashboardActions.loadDashboard),
      switchMap(() =>
        forkJoin({
          pmcData: this.pmcService.getPmc(),
          recentRidesPage: this.ridesService.listRides(0, 10),
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
        )
      )
    )
  );
}
