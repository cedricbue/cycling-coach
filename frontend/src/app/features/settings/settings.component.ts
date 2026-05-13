import { Component, inject, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { ActivatedRoute, Router } from '@angular/router';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTabsModule, MatTabChangeEvent } from '@angular/material/tabs';
import { SettingsActions } from './+state/settings.actions';
import {
  selectPowerZones,
  selectCurrentFtp,
  selectHrZones,
  selectMaxHr,
  selectSettingsLoading,
} from './+state/settings.selectors';
import { PowerZonesTableComponent } from './components/power-zones/power-zones-table.component';
import { HrZonesTableComponent } from './components/hr-zones/hr-zones-table.component';

const TABS = ['power', 'hr'] as const;
type Tab = typeof TABS[number];

@Component({
  selector: 'app-settings',
  imports: [
    MatProgressSpinnerModule,
    MatTabsModule,
    PowerZonesTableComponent,
    HrZonesTableComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = this.store.selectSignal(selectSettingsLoading);
  readonly powerZones = this.store.selectSignal(selectPowerZones);
  readonly currentFtp = this.store.selectSignal(selectCurrentFtp);
  readonly hrZones = this.store.selectSignal(selectHrZones);
  readonly maxHr = this.store.selectSignal(selectMaxHr);

  selectedIndex = 0;

  ngOnInit(): void {
    this.store.dispatch(SettingsActions.loadSettings());
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
