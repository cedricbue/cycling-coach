import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { EMPTY, Observable } from 'rxjs';
import { catchError, map, mergeMap, of, switchMap } from 'rxjs';
import { BikeFitActions } from './bike-fit.actions';
import { BikeFitService } from '../../../core/api/api/bikeFit.service';

@Injectable()
export class BikeFitEffects {
  private readonly actions$ = inject(Actions);
  private readonly bikeFitService = inject(BikeFitService);

  readonly loadAnalyses$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.loadAnalyses),
      switchMap(() =>
        this.bikeFitService.listBikeFitAnalyses().pipe(
          map((analyses) => BikeFitActions.loadAnalysesSuccess({ analyses })),
          catchError((error) =>
            of(BikeFitActions.loadAnalysesFailure({ error: error?.message ?? 'Unknown error' }))
          )
        )
      )
    )
  );

  readonly uploadAnalysis$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.uploadAnalysis),
      switchMap(({ video, poseModel, mediapipeComplexity, rtmposeMode, rtmposeSchema, device }) =>
        this.bikeFitService
          .createBikeFitAnalysis(video, poseModel, mediapipeComplexity, rtmposeMode, rtmposeSchema, device)
          .pipe(
            map((analysis) => BikeFitActions.uploadAnalysisSuccess({ analysis })),
            catchError((error) =>
              of(BikeFitActions.uploadAnalysisFailure({ error: error?.message ?? 'Upload failed' }))
            )
          )
      )
    )
  );

  readonly watchSseOnUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.uploadAnalysisSuccess),
      mergeMap(({ analysis }) => {
        if (analysis.status !== 'PROCESSING' || !analysis.id) return EMPTY;
        return new Observable<ReturnType<typeof BikeFitActions.analysisStatusUpdated>>((subscriber) => {
          const es = new EventSource(`/api/bike-fit/analyses/${analysis.id}/events`);
          es.onmessage = (event) => {
            try {
              const data = JSON.parse(event.data);
              subscriber.next(
                BikeFitActions.analysisStatusUpdated({
                  id: analysis.id!,
                  status: data.status,
                })
              );
            } catch {
              // ignore unparseable event
            }
            es.close();
            subscriber.complete();
          };
          es.onerror = () => {
            es.close();
            subscriber.complete();
          };
          return () => es.close();
        });
      })
    )
  );

  readonly loadDetail$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.loadAnalysisDetail),
      switchMap(({ id }) =>
        this.bikeFitService.getBikeFitAnalysis(id).pipe(
          map((detail) => BikeFitActions.loadAnalysisDetailSuccess({ detail })),
          catchError((error) =>
            of(BikeFitActions.loadAnalysisDetailFailure({ error: error?.message ?? 'Unknown error' }))
          )
        )
      )
    )
  );
}
