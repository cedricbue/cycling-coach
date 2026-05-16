import { inject, Injectable } from '@angular/core';
import { Actions, createEffect, ofType, OnInitEffects } from '@ngrx/effects';
import { Action } from '@ngrx/store';
import { EMPTY, from, Observable } from 'rxjs';
import { catchError, map, mergeMap, of, switchMap } from 'rxjs';
import { BikeFitActions } from './bike-fit.actions';
import { BikeFitService } from '../../../core/api/api/bikeFit.service';
import { BikeFitAnalysisSummary } from '../../../core/api/model/models';

function sseObservable(id: string): Observable<ReturnType<typeof BikeFitActions.analysisStatusUpdated>> {
  return new Observable((subscriber) => {
    const es = new EventSource(`/api/bike-fit/analyses/${id}/events`);
    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        subscriber.next(BikeFitActions.analysisStatusUpdated({ id, status: data.status }));
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
}

@Injectable()
export class BikeFitEffects implements OnInitEffects {
  private readonly actions$ = inject(Actions);
  private readonly bikeFitService = inject(BikeFitService);

  ngrxOnInitEffects(): Action {
    return BikeFitActions.loadAnalyses();
  }

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

  // Subscribe to SSE for a newly uploaded PROCESSING analysis
  readonly watchSseOnUpload$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.uploadAnalysisSuccess),
      mergeMap(({ analysis }) => {
        if (analysis.status !== 'PROCESSING' || !analysis.id) return EMPTY;
        return sseObservable(analysis.id);
      })
    )
  );

  // On load (including page refresh), reconnect SSE for any already-PROCESSING analyses
  readonly reconnectSseOnLoad$ = createEffect(() =>
    this.actions$.pipe(
      ofType(BikeFitActions.loadAnalysesSuccess),
      mergeMap(({ analyses }) => {
        const processing = analyses.filter(
          (a): a is BikeFitAnalysisSummary & { id: string } =>
            a.status === 'PROCESSING' && !!a.id
        );
        if (processing.length === 0) return EMPTY;
        return from(processing).pipe(mergeMap((a) => sseObservable(a.id)));
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
