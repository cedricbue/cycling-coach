import { Component, inject, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTabsModule, MatTabChangeEvent } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ProfileActions } from './+state/profile.actions';
import {
  selectFtpHistory,
  selectWeightHistory,
  selectProfileLoading,
} from './+state/profile.selectors';
import { FtpHistoryComponent } from './components/ftp-history/ftp-history.component';
import { WeightHistoryComponent } from './components/weight-history/weight-history.component';

const TABS = ['ftp', 'weight'] as const;
type Tab = typeof TABS[number];

@Component({
  selector: 'app-user-profile',
  imports: [
    MatTabsModule,
    MatProgressSpinnerModule,
    FtpHistoryComponent,
    WeightHistoryComponent,
  ],
  templateUrl: './user-profile.component.html',
  styleUrl: './user-profile.component.scss',
})
export class UserProfileComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = this.store.selectSignal(selectProfileLoading);
  readonly ftpHistory = this.store.selectSignal(selectFtpHistory);
  readonly weightHistory = this.store.selectSignal(selectWeightHistory);

  selectedIndex = 0;

  ngOnInit(): void {
    this.store.dispatch(ProfileActions.loadProfile());
    const tab = this.route.snapshot.queryParamMap.get('tab') as Tab | null;
    this.selectedIndex = tab ? Math.max(0, TABS.indexOf(tab)) : 0;
  }

  onTabChange(event: MatTabChangeEvent): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: TABS[event.index] },
      replaceUrl: true,
    });
  }
}
