import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { of } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import { RidesActions } from './rides.actions';
import { RidesService } from '../../../core/api';

@Injectable()
export class RidesEffects {
  private readonly actions$ = inject(Actions);
  private readonly ridesService = inject(RidesService);

  readonly loadRides$ = createEffect(() =>
    this.actions$.pipe(
      ofType(RidesActions.loadRides),
      switchMap(({ page, size }) =>
        this.ridesService.listRides(page, size).pipe(
          map((ridePage) => RidesActions.loadRidesSuccess({ ridePage })),
          catchError((err) =>
            of(RidesActions.loadRidesFailure({ error: err?.message ?? 'Failed to load rides' }))
          )
        )
      )
    )
  );

  readonly loadRide$ = createEffect(() =>
    this.actions$.pipe(
      ofType(RidesActions.loadRide),
      switchMap(({ id }) =>
        this.ridesService.getRide(id).pipe(
          map((ride) => RidesActions.loadRideSuccess({ ride })),
          catchError((err) =>
            of(RidesActions.loadRideFailure({ error: err?.message ?? 'Failed to load ride' }))
          )
        )
      )
    )
  );
}
