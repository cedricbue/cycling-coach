import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { SYNC_FEATURE_KEY, syncReducer } from './features/activities/+state/sync.reducer';
import { SyncEffects } from './features/activities/+state/sync.effects';
import { RIDES_FEATURE_KEY, ridesReducer } from './features/rides/+state/rides.reducer';
import { RidesEffects } from './features/rides/+state/rides.effects';

export const routes: Routes = [
  {
    path: '',
    providers: [
      provideState(SYNC_FEATURE_KEY, syncReducer),
      provideEffects(SyncEffects),
      provideState(RIDES_FEATURE_KEY, ridesReducer),
      provideEffects(RidesEffects),
    ],
    loadComponent: () =>
      import('./features/home/home.component').then((m) => m.HomeComponent),
    children: [
      {
        path: '',
        redirectTo: 'rides',
        pathMatch: 'full',
      },
      {
        path: 'rides',
        loadComponent: () =>
          import('./features/rides/rides-list/rides-list.component').then(
            (m) => m.RidesListComponent
          ),
      },
      {
        path: 'rides/:id',
        loadComponent: () =>
          import('./features/rides/ride-detail/ride-detail.component').then(
            (m) => m.RideDetailComponent
          ),
      },
    ],
  },
];

