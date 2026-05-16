import { Routes } from '@angular/router';
import { provideState } from '@ngrx/store';
import { provideEffects } from '@ngrx/effects';
import { SYNC_FEATURE_KEY, syncReducer } from './features/activities/+state/sync.reducer';
import { SyncEffects } from './features/activities/+state/sync.effects';
import { RIDES_FEATURE_KEY, ridesReducer } from './features/rides/+state/rides.reducer';
import { RidesEffects } from './features/rides/+state/rides.effects';
import { DASHBOARD_FEATURE_KEY, dashboardReducer } from './features/dashboard/+state/dashboard.reducer';
import { DashboardEffects } from './features/dashboard/+state/dashboard.effects';
import { SETTINGS_FEATURE_KEY, settingsReducer } from './features/settings/+state/settings.reducer';
import { SettingsEffects } from './features/settings/+state/settings.effects';
import { PROFILE_FEATURE_KEY, profileReducer } from './features/user-profile/+state/profile.reducer';
import { ProfileEffects } from './features/user-profile/+state/profile.effects';
import { BIKE_FIT_FEATURE_KEY, bikeFitReducer } from './features/bike-fit/+state/bike-fit.reducer';
import { BikeFitEffects } from './features/bike-fit/+state/bike-fit.effects';

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
      import('./features/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        providers: [
          provideState(DASHBOARD_FEATURE_KEY, dashboardReducer),
          provideEffects(DashboardEffects),
        ],
        loadComponent: () =>
          import('./features/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          ),
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
      {
        path: 'settings',
        providers: [
          provideState(SETTINGS_FEATURE_KEY, settingsReducer),
          provideEffects(SettingsEffects),
        ],
        loadComponent: () =>
          import('./features/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
      },
      {
        path: 'profile',
        providers: [
          provideState(PROFILE_FEATURE_KEY, profileReducer),
          provideEffects(ProfileEffects),
        ],
        loadComponent: () =>
          import('./features/user-profile/user-profile.component').then(
            (m) => m.UserProfileComponent
          ),
      },
      {
        path: 'bike-fit',
        providers: [
          provideState(BIKE_FIT_FEATURE_KEY, bikeFitReducer),
          provideEffects(BikeFitEffects),
        ],
        loadComponent: () =>
          import('./features/bike-fit/bike-fit-list/bike-fit-list.component').then(
            (m) => m.BikeFitListComponent
          ),
      },
      {
        path: 'bike-fit/:id',
        providers: [
          provideState(BIKE_FIT_FEATURE_KEY, bikeFitReducer),
          provideEffects(BikeFitEffects),
        ],
        loadComponent: () =>
          import('./features/bike-fit/bike-fit-detail/bike-fit-detail.component').then(
            (m) => m.BikeFitDetailComponent
          ),
      },
    ],
  },
];

