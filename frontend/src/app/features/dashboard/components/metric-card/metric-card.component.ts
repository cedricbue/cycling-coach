import { Component, input } from '@angular/core';

export type MetricCardAccent = 'blue' | 'orange' | 'green' | 'neutral' | 'red';

@Component({
  selector: 'app-metric-card',
  imports: [],
  template: `
    <div class="metric-card cc-card" [class]="'accent-' + accent()">
      <div class="label">{{ label() }}</div>
      <div class="value">
        {{ value() !== null && value() !== undefined ? value() : '—' }}
        @if (unit()) {
          <span class="unit">{{ unit() }}</span>
        }
      </div>
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
  readonly subtitle = input<string>('');
  readonly accent = input<MetricCardAccent>('neutral');
}
