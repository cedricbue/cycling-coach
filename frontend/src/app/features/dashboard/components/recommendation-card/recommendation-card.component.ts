import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { DailyRecommendation } from '../../../../core/api/model/models';

@Component({
  selector: 'app-recommendation-card',
  imports: [DatePipe, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTooltipModule],
  templateUrl: './recommendation-card.component.html',
  styleUrl: './recommendation-card.component.scss',
})
export class RecommendationCardComponent {
  readonly recommendation = input<DailyRecommendation | null>(null);
  readonly loading = input<boolean>(false);
  readonly error = input<string | null>(null);
  readonly regenerate = output<void>();

  get typeIcon(): string {
    switch (this.recommendation()?.type) {
      case 'OUTDOOR': return 'directions_bike';
      case 'OUTDOOR_FUN': return 'explore';
      case 'INDOOR': return 'fitness_center';
      case 'REST': return 'hotel';
      default: return 'help_outline';
    }
  }

  get typeLabel(): string {
    switch (this.recommendation()?.type) {
      case 'OUTDOOR': return 'Outdoor Ride';
      case 'OUTDOOR_FUN': return 'Free Ride';
      case 'INDOOR': return 'Zwift Workout';
      case 'REST': return 'Rest Day';
      default: return '';
    }
  }

  get accentClass(): string {
    switch (this.recommendation()?.type) {
      case 'OUTDOOR': return 'accent-green';
      case 'OUTDOOR_FUN': return 'accent-teal';
      case 'INDOOR': return 'accent-blue';
      case 'REST': return 'accent-orange';
      default: return '';
    }
  }
}
