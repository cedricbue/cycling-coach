import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { forkJoin } from 'rxjs';
import { catchError, map, of, switchMap } from 'rxjs';
import { SettingsActions } from './settings.actions';
import { SettingsService } from '../../../core/api/api/settings.service';
import { FtpService } from '../../../core/api/api/ftp.service';

@Injectable()
export class SettingsEffects {
  private readonly actions$ = inject(Actions);
  private readonly settingsService = inject(SettingsService);
  private readonly ftpService = inject(FtpService);

  readonly loadSettings$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SettingsActions.loadSettings),
      switchMap(() =>
        forkJoin({
          appSettings: this.settingsService.getSettings(),
          ftpHistory: this.ftpService.getFtpHistory(),
        }).pipe(
          map(({ appSettings, ftpHistory }) =>
            SettingsActions.loadSettingsSuccess({ appSettings, ftpHistory })
          ),
          catchError((error) =>
            of(
              SettingsActions.loadSettingsFailure({
                error: error?.message ?? 'Unknown error',
              })
            )
          )
        )
      )
    )
  );
}
