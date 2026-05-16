import {
  Component,
  input,
  signal,
  computed,
  effect,
  ElementRef,
  viewChild,
  AfterViewInit,
  OnDestroy,
} from '@angular/core';
import { Chart, ChartConfiguration, registerables } from 'chart.js';
import { PmcDataPoint } from '../../../../core/api/model/models';

Chart.register(...registerables);

const ACCENT_BLUE = '#3f51b5';
const ACCENT_ORANGE = '#ff9800';
const ACCENT_GREEN = '#4caf50';

const SERIES = [
  { label: 'CTL — Fitness', color: ACCENT_BLUE,  index: 0 },
  { label: 'ATL — Fatigue', color: ACCENT_ORANGE, index: 1 },
  { label: 'TSB — Form',    color: ACCENT_GREEN,  index: 2 },
] as const;

const RANGE_90D = '90d' as const;
const RANGE_6M = '6m' as const;
const RANGE_1Y = '1y' as const;
const RANGE_ALL = 'all' as const;

type Range = typeof RANGE_90D | typeof RANGE_6M | typeof RANGE_1Y | typeof RANGE_ALL;

const RANGES: { label: string; value: Range }[] = [
  { label: '90d', value: RANGE_90D },
  { label: '6m', value: RANGE_6M },
  { label: '1y', value: RANGE_1Y },
  { label: 'All', value: RANGE_ALL },
];

@Component({
  selector: 'app-pmc-chart',
  imports: [],
  template: `
    <div class="cc-card chart-wrapper">
      <div class="chart-header">
        <h2 class="mat-title-medium">Performance Management Chart</h2>
        <div class="controls">
          <div class="range-selector">
            @for (r of ranges; track r.value) {
              <button
                class="range-btn"
                [class.active]="selectedRange() === r.value"
                (click)="setRange(r.value)"
              >{{ r.label }}</button>
            }
          </div>
          <div class="legend">
            @for (s of series; track s.index) {
              <button
                class="legend-item"
                [class.hidden]="hiddenSeries().has(s.index)"
                (click)="toggleSeries(s.index)"
                type="button"
              >
                <span class="legend-dot" [style.background]="s.color"></span>{{ s.label }}
              </button>
            }
          </div>
        </div>
      </div>
      <canvas #chartCanvas></canvas>
    </div>
  `,
  styleUrl: './pmc-chart.component.scss',
})
export class PmcChartComponent implements AfterViewInit, OnDestroy {
  readonly data = input<PmcDataPoint[]>([]);
  readonly ranges = RANGES;
  readonly series = SERIES;

  readonly selectedRange = signal<Range>(RANGE_90D);
  readonly hiddenSeries = signal<Set<number>>(new Set());

  readonly filteredData = computed(() => {
    const points = this.data();
    const range = this.selectedRange();
    if (points.length === 0) return points;
    if (range === RANGE_ALL) {
      const firstMeaningful = points.findIndex(
        (p) => (p.ctl ?? 0) > 0 || (p.atl ?? 0) > 0,
      );
      return firstMeaningful > 0 ? points.slice(firstMeaningful - 1) : points;
    }

    const days = range === RANGE_90D ? 90 : range === RANGE_6M ? 182 : 365;
    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - days);
    const cutoffStr = cutoff.toISOString().split('T')[0];
    return points.filter((p) => (p.date ?? '') >= cutoffStr);
  });

  private readonly chartCanvas =
    viewChild.required<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const points = this.filteredData();
      if (this.chart) {
        this.updateChart(points);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initChart(this.filteredData());
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  setRange(range: Range): void {
    this.selectedRange.set(range);
  }

  toggleSeries(index: number): void {
    this.hiddenSeries.update((prev) => {
      const next = new Set(prev);
      if (next.has(index)) {
        next.delete(index);
      } else {
        next.add(index);
      }
      return next;
    });
    this.chart?.setDatasetVisibility(index, !this.hiddenSeries().has(index));
    this.chart?.update();
  }

  private initChart(points: PmcDataPoint[]): void {
    const ctx = this.chartCanvas().nativeElement.getContext('2d');
    if (!ctx) return;

    const config: ChartConfiguration = {
      type: 'line',
      data: this.buildDatasets(points),
      options: {
        responsive: true,
        maintainAspectRatio: true,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (ctx) => {
                const v = ctx.parsed.y as number;
                return `${ctx.dataset.label}: ${Math.round(v)}`;
              },
            },
          },
        },
        scales: {
          x: {
            ticks: { maxTicksLimit: 10, font: { size: 11 } },
            grid: { color: 'rgba(0,0,0,0.05)' },
          },
          y: {
            position: 'left',
            ticks: { font: { size: 11 } },
            grid: {
              color: (ctx) =>
                ctx.tick.value === 0 ? 'rgba(0,0,0,0.35)' : 'rgba(0,0,0,0.05)',
              lineWidth: (ctx) => (ctx.tick.value === 0 ? 1.5 : 1),
            },
          },
        },
      },
    };

    this.chart = new Chart(ctx, config);
  }

  private updateChart(points: PmcDataPoint[]): void {
    if (!this.chart) return;
    this.chart.data = this.buildDatasets(points);
    this.chart.update('none');
    // Re-apply hidden state — data rebuild resets Chart.js internal visibility
    this.hiddenSeries().forEach((i) => this.chart?.setDatasetVisibility(i, false));
    this.chart?.update('none');
  }

  private buildDatasets(points: PmcDataPoint[]) {
    const labels = points.map((p) => p.date ?? '');

    return {
      labels,
      datasets: [
        {
          label: 'CTL — Fitness',
          data: points.map((p) => p.ctl ?? 0),
          borderColor: ACCENT_BLUE,
          backgroundColor: 'rgba(63,81,181,0.08)',
          fill: true,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: 'y',
        },
        {
          label: 'ATL — Fatigue',
          data: points.map((p) => p.atl ?? 0),
          borderColor: ACCENT_ORANGE,
          backgroundColor: 'rgba(255,152,0,0.08)',
          fill: true,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: 'y',
        },
        {
          label: 'TSB — Form',
          data: points.map((p) => p.tsb ?? 0),
          borderColor: ACCENT_GREEN,
          backgroundColor: 'rgba(76,175,80,0.08)',
          fill: false,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
          yAxisID: 'y',
        },
      ],
    };
  }
}
