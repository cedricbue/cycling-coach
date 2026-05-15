import { Component, inject, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { DecimalPipe } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { DashboardActions } from './+state/dashboard.actions';
import {
  selectDashboardLoading,
  selectLatestPmc,
  selectCurrentFtp,
  selectPmcData,
  selectRecentRides,
  selectFtpPerKg,
  selectRecommendation,
  selectRecommendationLoading,
  selectRecommendationError,
} from './+state/dashboard.selectors';
import { MetricCardComponent } from './components/metric-card/metric-card.component';
import { PmcChartComponent } from './components/pmc-chart/pmc-chart.component';
import { RecentRidesComponent } from './components/recent-rides/recent-rides.component';
import { RecommendationCardComponent } from './components/recommendation-card/recommendation-card.component';

@Component({
  selector: 'app-dashboard',
  imports: [
    DecimalPipe,
    MatProgressSpinnerModule,
    MetricCardComponent,
    PmcChartComponent,
    RecentRidesComponent,
    RecommendationCardComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly store = inject(Store);

  readonly loading = this.store.selectSignal(selectDashboardLoading);
  readonly latestPmc = this.store.selectSignal(selectLatestPmc);
  readonly currentFtp = this.store.selectSignal(selectCurrentFtp);
  readonly ftpPerKg = this.store.selectSignal(selectFtpPerKg);
  readonly pmcData = this.store.selectSignal(selectPmcData);
  readonly recentRides = this.store.selectSignal(selectRecentRides);
  readonly recommendation = this.store.selectSignal(selectRecommendation);
  readonly recommendationLoading = this.store.selectSignal(selectRecommendationLoading);
  readonly recommendationError = this.store.selectSignal(selectRecommendationError);

  ngOnInit(): void {
    this.store.dispatch(DashboardActions.loadDashboard());
    this.store.dispatch(DashboardActions.loadRecommendation({ regenerate: false }));
  }

  onRegenerate(): void {
    this.store.dispatch(DashboardActions.regenerateRecommendation());
  }

  get tsbSubtitle(): string {
    const tsb = this.latestPmc()?.tsb;
    if (tsb === undefined || tsb === null) return '';
    if (tsb > 10) return 'Fresh — possibly under-trained';
    if (tsb >= 0) return 'Ready to perform';
    if (tsb >= -10) return 'Slightly fatigued';
    return 'High fatigue — rest recommended';
  }

  get tsbAccent(): 'green' | 'blue' | 'orange' | 'red' {
    const tsb = this.latestPmc()?.tsb;
    if (tsb === undefined || tsb === null) return 'green';
    if (tsb > 10) return 'blue';
    if (tsb >= 0) return 'green';
    if (tsb >= -10) return 'orange';
    return 'red';
  }
}
