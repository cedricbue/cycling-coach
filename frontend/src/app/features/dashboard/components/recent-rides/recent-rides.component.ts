import { Component, input } from '@angular/core';
import { DecimalPipe, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { RideSummary } from '../../../../core/api/model/models';

@Component({
  selector: 'app-recent-rides',
  imports: [DecimalPipe, DatePipe, RouterLink],
  templateUrl: './recent-rides.component.html',
  styleUrl: './recent-rides.component.scss',
})
export class RecentRidesComponent {
  readonly rides = input<RideSummary[]>([]);

  formatDuration(seconds?: number): string {
    if (!seconds) return '—';
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    return h > 0 ? `${h}h ${m}m` : `${m}m`;
  }

  tsbClass(if_: number | undefined): string {
    if (!if_) return '';
    if (if_ >= 0.9) return 'badge-hard';
    if (if_ >= 0.75) return 'badge-moderate';
    return 'badge-easy';
  }
}
