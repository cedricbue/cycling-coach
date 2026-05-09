import { Component, OnInit, inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { Router } from '@angular/router';
import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { RidesActions } from '../+state/rides.actions';
import {
  selectRidesList,
  selectRidesListLoading,
  selectRidesListError,
  selectRidesTotalElements,
} from '../+state/rides.selectors';

@Component({
  selector: 'app-rides-list',
  imports: [DatePipe, DecimalPipe, MatTableModule, MatProgressSpinnerModule, MatIconModule],
  templateUrl: './rides-list.component.html',
  styleUrl: './rides-list.component.scss',
})
export class RidesListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  readonly rides = this.store.selectSignal(selectRidesList);
  readonly loading = this.store.selectSignal(selectRidesListLoading);
  readonly error = this.store.selectSignal(selectRidesListError);
  readonly totalElements = this.store.selectSignal(selectRidesTotalElements);

  readonly displayedColumns = ['date', 'name', 'distance', 'duration', 'avgPower', 'tss', 'if'];

  ngOnInit(): void {
    this.store.dispatch(RidesActions.loadRides({ page: 0, size: 50 }));
  }

  goToDetail(id: number | undefined): void {
    if (id != null) {
      this.router.navigate(['/rides', id]);
    }
  }

  formatDuration(seconds: number | undefined): string {
    if (seconds == null) return '—';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    return h > 0
      ? `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
      : `${m}:${s.toString().padStart(2, '0')}`;
  }
}
