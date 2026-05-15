import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { forkJoin, from, of, throwError, timer } from 'rxjs';
import { catchError, map, retry, switchMap } from 'rxjs';
import { DashboardActions } from './dashboard.actions';
import { PmcService } from '../../../core/api/api/pmc.service';
import { RidesService } from '../../../core/api/api/rides.service';
import { FtpService } from '../../../core/api/api/ftp.service';
import { SettingsService } from '../../../core/api/api/settings.service';
import { CoachingService } from '../../../core/api/api/coaching.service';

@Injectable()
export class DashboardEffects {
  private readonly actions$ = inject(Actions);
  private readonly pmcService = inject(PmcService);
  private readonly ridesService = inject(RidesService);
  private readonly ftpService = inject(FtpService);
  private readonly settingsService = inject(SettingsService);
  private readonly coachingService = inject(CoachingService);

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

  readonly loadRecommendation$ = createEffect(() =>
    this.actions$.pipe(
      ofType(DashboardActions.loadRecommendation, DashboardActions.regenerateRecommendation),
      switchMap((action) => {
        const regenerate = 'regenerate' in action ? action.regenerate : true;

        // Try to get browser location. On M-series Macs CoreLocation often fails
        // (kCLErrorLocationUnknown) even with permissions — retry once, then fall back to
        // calling the backend without coords (backend uses COACHING_LAT/COACHING_LON).
        const geolocation$ = from(
          new Promise<GeolocationPosition | null>((resolve) =>
            navigator.geolocation.getCurrentPosition(
              resolve,
              () => resolve(null),     // any error → resolve with null, never reject
              { timeout: 10000, maximumAge: 300000 }
            )
          )
        );

        return geolocation$.pipe(
          switchMap((pos) =>
            this.coachingService.getDailyRecommendation(
              pos?.coords.latitude ?? undefined,
              pos?.coords.longitude ?? undefined,
              regenerate
            )
          ),
          map((recommendation) => DashboardActions.loadRecommendationSuccess({ recommendation })),
          catchError((err) =>
            of(DashboardActions.loadRecommendationFailure({
              error: err?.message ?? 'Could not load recommendation.',
            }))
          )
        );
      })
    )
  );
}
