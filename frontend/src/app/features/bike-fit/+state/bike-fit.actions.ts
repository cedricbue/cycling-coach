import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { BikeFitAnalysisDetail, BikeFitAnalysisSummary } from '../../../core/api/model/models';

export const BikeFitActions = createActionGroup({
  source: 'BikeFit',
  events: {
    'Load Analyses': emptyProps(),
    'Load Analyses Success': props<{ analyses: BikeFitAnalysisSummary[] }>(),
    'Load Analyses Failure': props<{ error: string }>(),

    'Upload Analysis': props<{
      video: Blob;
      filename: string;
      poseModel: string;
      mediapipeComplexity?: number;
      rtmposeMode?: string;
      rtmposeSchema?: string;
      device: string;
    }>(),
    'Upload Analysis Success': props<{ analysis: BikeFitAnalysisSummary }>(),
    'Upload Analysis Failure': props<{ error: string }>(),

    'Analysis Status Updated': props<{ id: string; status: 'DONE' | 'FAILED' }>(),

    'Load Analysis Detail': props<{ id: string }>(),
    'Load Analysis Detail Success': props<{ detail: BikeFitAnalysisDetail }>(),
    'Load Analysis Detail Failure': props<{ error: string }>(),

    'Clear Detail': emptyProps(),
  },
});
