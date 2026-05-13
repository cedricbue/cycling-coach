import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { catchError, map, of, switchMap } from 'rxjs';
import { SettingsActions } from './settings.actions';
import { SettingsService } from '../../../core/api/api/settings.service';

@Injectable()
export class SettingsEffects {
  private readonly actions$ = inject(Actions);
  private readonly settingsService = inject(SettingsService);

  readonly loadSettings$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SettingsActions.loadSettings),
      switchMap(() =>
        this.settingsService.getSettings().pipe(
          map((appSettings) =>
            SettingsActions.loadSettingsSuccess({ appSettings })
          ),
          catchError((error) =>
            of(SettingsActions.loadSettingsFailure({
              error: error?.message ?? 'Unknown error',
            }))
          )
        )
      )
    )
  );
}
