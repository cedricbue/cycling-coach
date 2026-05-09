import { Component, inject, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { MatTabsModule } from '@angular/material/tabs';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SettingsActions } from './+state/settings.actions';
import {
  selectPowerZones,
  selectFtpHistory,
  selectSettingsLoading,
  selectCurrentFtp,
} from './+state/settings.selectors';
import { PowerZonesTableComponent } from './components/power-zones/power-zones-table.component';
import { FtpHistoryComponent } from './components/ftp-history/ftp-history.component';

@Component({
  selector: 'app-settings',
  imports: [
    MatTabsModule,
    MatProgressSpinnerModule,
    PowerZonesTableComponent,
    FtpHistoryComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  private readonly store = inject(Store);

  readonly loading = this.store.selectSignal(selectSettingsLoading);
  readonly powerZones = this.store.selectSignal(selectPowerZones);
  readonly ftpHistory = this.store.selectSignal(selectFtpHistory);
  readonly currentFtp = this.store.selectSignal(selectCurrentFtp);

  ngOnInit(): void {
    this.store.dispatch(SettingsActions.loadSettings());
  }
}
