import {
  Component,
  input,
  effect,
  ElementRef,
  viewChild,
  AfterViewInit,
  OnDestroy,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { WeightEntry } from '../../../../core/api/model/models';

Chart.register(...registerables);

const ACCENT_TEAL = '#009688';

@Component({
  selector: 'app-weight-history',
  imports: [],
  template: `
    @if (history().length === 0) {
      <p class="empty-state">No weight entries yet. Sync Garmin data to populate weight history.</p>
    }
    <canvas #chartCanvas [class.hidden]="history().length === 0"></canvas>
  `,
  styleUrl: './weight-history.component.scss',
})
export class WeightHistoryComponent implements AfterViewInit, OnDestroy {
  readonly history = input<WeightEntry[]>([]);

  private readonly chartCanvas =
    viewChild<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const entries = this.sorted();
      if (entries.length === 0) return;
      if (this.chart) {
        this.updateChart(entries);
      } else {
        // Data arrived after view init (NgRx async load) — canvas is in DOM, init now
        this.initChart(entries);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initChart(this.sorted());
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private sorted(): WeightEntry[] {
    return [...this.history()].sort((a, b) =>
      (a.date ?? '').localeCompare(b.date ?? '')
    );
  }

  private initChart(entries: WeightEntry[]): void {
    const canvas = this.chartCanvas();
    if (!canvas) return;
    const ctx = canvas.nativeElement.getContext('2d');
    if (!ctx) return;

    const config: ChartConfiguration = {
      type: 'line',
      data: this.buildDatasets(entries),
      options: this.buildOptions(entries),
    };

    this.chart = new Chart(ctx, config);
  }

  private updateChart(entries: WeightEntry[]): void {
    if (!this.chart) return;
    this.chart.data = this.buildDatasets(entries);
    Object.assign(this.chart.options, this.buildOptions(entries));
    this.chart.update('none');
  }

  private buildDatasets(entries: WeightEntry[]) {
    return {
      labels: entries.map((e) => e.date ?? ''),
      datasets: [
        {
          label: 'Weight',
          data: entries.map((e) => e.weightKg ?? null),
          borderColor: ACCENT_TEAL,
          backgroundColor: 'rgba(0,150,136,0.08)',
          fill: true,
          tension: 0.3,
          borderWidth: 2,
          pointRadius: entries.length > 60 ? 0 : 3,
          pointHoverRadius: 5,
        },
      ],
    };
  }

  private buildOptions(entries: WeightEntry[]): ChartConfiguration['options'] {
    const weights = entries.map((e) => e.weightKg ?? 0).filter((w) => w > 0);
    const min = weights.length ? Math.floor(Math.min(...weights)) - 2 : undefined;
    const max = weights.length ? Math.ceil(Math.max(...weights)) + 2 : undefined;

    return {
      responsive: true,
      maintainAspectRatio: true,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (items) => formatDate(items[0]?.label ?? ''),
            label: (ctx) => `Weight: ${(ctx.parsed.y as number).toFixed(1)} kg`,
          },
        },
      },
      scales: {
        x: {
          ticks: {
            maxTicksLimit: 12,
            font: { size: 11 },
            callback: function (_, index) {
              const label = (this.getLabelForValue(index) as string) ?? '';
              return formatMonthYear(label);
            },
          },
          grid: { color: 'rgba(0,0,0,0.05)' },
        },
        y: {
          min,
          max,
          ticks: { font: { size: 11 }, callback: (v) => `${v} kg` },
          grid: { color: 'rgba(0,0,0,0.05)' },
        },
      },
    };
  }
}

function formatDate(iso: string): string {
  if (!iso) return '';
  const [year, month, day] = iso.split('-');
  const d = new Date(+year, +month - 1, +day);
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
}

function formatMonthYear(iso: string): string {
  if (!iso) return '';
  const [year, month] = iso.split('-');
  const d = new Date(+year, +month - 1, 1);
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short' });
}
