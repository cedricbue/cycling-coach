import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

export type MetricCardAccent = 'blue' | 'orange' | 'green' | 'neutral' | 'red';

@Component({
  selector: 'app-metric-card',
  imports: [MatIconModule, MatTooltipModule],
  template: `
    <div class="metric-card cc-card" [class]="'accent-' + accent()">
      <div class="label-row">
        <span class="label">{{ label() }}</span>
        @if (description()) {
          <mat-icon
            class="info-icon"
            [matTooltip]="description()"
            matTooltipPosition="above"
            matTooltipShowDelay="100"
          >info_outline</mat-icon>
        }
      </div>
      <div class="value">
        {{ value() !== null && value() !== undefined ? value() : '—' }}
        @if (unit()) {
          <span class="unit">{{ unit() }}</span>
        }
      </div>
      @if (secondaryValue()) {
        <div class="secondary-value">
          {{ secondaryValue() }}
          @if (secondaryUnit()) {
            <span class="secondary-unit">{{ secondaryUnit() }}</span>
          }
        </div>
      }
      @if (subtitle()) {
        <div class="subtitle">{{ subtitle() }}</div>
      }
    </div>
  `,
  styleUrl: './metric-card.component.scss',
})
export class MetricCardComponent {
  readonly label = input.required<string>();
  readonly value = input<number | string | null | undefined>();
  readonly unit = input<string>('');
  readonly secondaryValue = input<string | null | undefined>();
  readonly secondaryUnit = input<string>('');
  readonly subtitle = input<string>('');
  readonly description = input<string>('');
  readonly accent = input<MetricCardAccent>('neutral');
}
