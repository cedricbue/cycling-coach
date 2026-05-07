import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { SYNC_FEATURE_KEY, syncReducer } from './features/activities/+state/sync.reducer';
import { SyncEffects } from './features/activities/+state/sync.effects';

export const routes: Routes = [
  {
    path: '',
    providers: [
      provideState(SYNC_FEATURE_KEY, syncReducer),
      provideEffects(SyncEffects),
    ],
    loadComponent: () =>
      import('./features/home/home.component').then(
        (m) => m.HomeComponent
      ),
  },

];
