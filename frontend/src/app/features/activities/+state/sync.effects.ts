import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, exhaustMap } from 'rxjs/operators';
import { SyncActions } from './sync.actions';
import { GarminService } from '../../../core/api';

@Injectable()
export class SyncEffects {
  private readonly actions$ = inject(Actions);
  private readonly garminService = inject(GarminService);

  readonly triggerSync$ = createEffect(() =>
    this.actions$.pipe(
      ofType(SyncActions.triggerSync),
      exhaustMap(() =>
        this.garminService.triggerGarminSync().pipe(
          map(() => SyncActions.syncEnqueued()),
          catchError((err) =>
            of(SyncActions.syncFailed({ error: err?.message ?? 'Sync failed' }))
          )
        )
      )
    )
  );
}
