import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { DatePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RidesActions } from '../+state/rides.actions';
import {
  selectRideDetail,
  selectRideDetailLoading,
  selectRideDetailError,
} from '../+state/rides.selectors';

@Component({
  selector: 'app-ride-detail',
  imports: [DatePipe, MatCardModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './ride-detail.component.html',
  styleUrl: './ride-detail.component.scss',
})
export class RideDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly ride = this.store.selectSignal(selectRideDetail);
  readonly loading = this.store.selectSignal(selectRideDetailLoading);
  readonly error = this.store.selectSignal(selectRideDetailError);

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.store.dispatch(RidesActions.loadRide({ id }));
  }

  goBack(): void {
    this.router.navigate(['/rides']);
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

  fmt(val: number | undefined, decimals = 1): string {
    return val != null ? val.toFixed(decimals) : '—';
  }

  fmtInt(val: number | undefined): string {
    return val != null ? Math.round(val).toString() : '—';
  }
}
