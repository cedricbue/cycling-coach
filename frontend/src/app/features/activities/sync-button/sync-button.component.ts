import { Component, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { SyncActions } from '../+state/sync.actions';
import { selectSyncing, selectSyncError } from '../+state/sync.selectors';

@Component({
  selector: 'app-sync-button',
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './sync-button.component.html',
  styleUrl: './sync-button.component.scss',
})
export class SyncButtonComponent {
  private readonly store = inject(Store);
  readonly syncing = this.store.selectSignal(selectSyncing);
  readonly syncError = this.store.selectSignal(selectSyncError);

  onSync(): void {
    this.store.dispatch(SyncActions.triggerSync());
  }
}
