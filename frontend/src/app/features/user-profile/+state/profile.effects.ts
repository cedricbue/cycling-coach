import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { forkJoin } from 'rxjs';
import { catchError, map, of, switchMap } from 'rxjs';
import { ProfileActions } from './profile.actions';
import { FtpService } from '../../../core/api/api/ftp.service';
import { WeightService } from '../../../core/api/api/weight.service';

@Injectable()
export class ProfileEffects {
  private readonly actions$ = inject(Actions);
  private readonly ftpService = inject(FtpService);
  private readonly weightService = inject(WeightService);

  readonly loadProfile$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProfileActions.loadProfile),
      switchMap(() =>
        forkJoin({
          ftpHistory: this.ftpService.getFtpHistory(),
          weightHistory: this.weightService.getWeightHistory(),
        }).pipe(
          map(({ ftpHistory, weightHistory }) =>
            ProfileActions.loadProfileSuccess({ ftpHistory, weightHistory })
          ),
          catchError((error) =>
            of(ProfileActions.loadProfileFailure({
              error: error?.message ?? 'Unknown error',
            }))
          )
        )
      )
    )
  );
}
