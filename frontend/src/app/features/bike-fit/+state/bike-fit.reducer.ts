import { createReducer, on } from '@ngrx/store';
import { BikeFitAnalysisDetail, BikeFitAnalysisSummary } from '../../../core/api/model/models';
import { BikeFitActions } from './bike-fit.actions';

export const BIKE_FIT_FEATURE_KEY = 'bikeFit';

export interface BikeFitState {
  analyses: BikeFitAnalysisSummary[];
  listLoading: boolean;
  listError: string | null;
  uploading: boolean;
  uploadError: string | null;
  detail: BikeFitAnalysisDetail | null;
  detailLoading: boolean;
  detailError: string | null;
}

const initialState: BikeFitState = {
  analyses: [],
  listLoading: false,
  listError: null,
  uploading: false,
  uploadError: null,
  detail: null,
  detailLoading: false,
  detailError: null,
};

export const bikeFitReducer = createReducer(
  initialState,

  on(BikeFitActions.loadAnalyses, (state) => ({
    ...state,
    listLoading: true,
    listError: null,
  })),
  on(BikeFitActions.loadAnalysesSuccess, (state, { analyses }) => ({
    ...state,
    analyses,
    listLoading: false,
  })),
  on(BikeFitActions.loadAnalysesFailure, (state, { error }) => ({
    ...state,
    listLoading: false,
    listError: error,
  })),

  on(BikeFitActions.uploadAnalysis, (state) => ({
    ...state,
    uploading: true,
    uploadError: null,
  })),
  on(BikeFitActions.uploadAnalysisSuccess, (state, { analysis }) => ({
    ...state,
    uploading: false,
    analyses: [analysis, ...state.analyses],
  })),
  on(BikeFitActions.uploadAnalysisFailure, (state, { error }) => ({
    ...state,
    uploading: false,
    uploadError: error,
  })),

  on(BikeFitActions.analysisStatusUpdated, (state, { id, status }) => ({
    ...state,
    analyses: state.analyses.map((a) =>
      a.id === id ? { ...a, status: status as BikeFitAnalysisSummary['status'] } : a
    ),
  })),

  on(BikeFitActions.loadAnalysisDetail, (state) => ({
    ...state,
    detailLoading: true,
    detailError: null,
  })),
  on(BikeFitActions.loadAnalysisDetailSuccess, (state, { detail }) => ({
    ...state,
    detail,
    detailLoading: false,
  })),
  on(BikeFitActions.loadAnalysisDetailFailure, (state, { error }) => ({
    ...state,
    detailLoading: false,
    detailError: error,
  })),

  on(BikeFitActions.clearDetail, (state) => ({
    ...state,
    detail: null,
    detailError: null,
  }))
);
