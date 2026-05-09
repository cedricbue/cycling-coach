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
import { PmcDataPoint } from '../../../../core/api/model/models';

Chart.register(...registerables);

@Component({
  selector: 'app-pmc-chart',
  imports: [],
  template: `
    <div class="cc-card chart-wrapper">
      <div class="chart-header">
        <h2 class="mat-title-medium">Performance Management Chart</h2>
        <div class="legend">
          <span class="legend-dot" style="background:#3f51b5"></span>CTL (Fitness)
          <span class="legend-dot" style="background:#ff9800"></span>ATL (Fatigue)
          <span class="legend-dot" style="background:#4caf50"></span>TSB (Form)
        </div>
      </div>
      <canvas #chartCanvas></canvas>
    </div>
  `,
  styleUrl: './pmc-chart.component.scss',
})
export class PmcChartComponent implements AfterViewInit, OnDestroy {
  readonly data = input<PmcDataPoint[]>([]);

  private readonly chartCanvas =
    viewChild.required<ElementRef<HTMLCanvasElement>>('chartCanvas');
  private chart: Chart | null = null;

  constructor() {
    effect(() => {
      const points = this.data();
      if (this.chart) {
        this.updateChart(points);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initChart(this.data());
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
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
              label: (ctx) =>
                `${ctx.dataset.label}: ${Math.round(ctx.parsed.y as number)}`,
            },
          },
        },
        scales: {
          x: {
            ticks: { maxTicksLimit: 10, font: { size: 11 } },
            grid: { color: 'rgba(0,0,0,0.05)' },
          },
          y: {
            ticks: { font: { size: 11 } },
            grid: { color: 'rgba(0,0,0,0.05)' },
          },
        },
      },
    };

    this.chart = new Chart(ctx, config);
  }

  private updateChart(points: PmcDataPoint[]): void {
    if (!this.chart) return;
    const datasets = this.buildDatasets(points);
    this.chart.data = datasets;
    this.chart.update('none');
  }

  private buildDatasets(points: PmcDataPoint[]) {
    const labels = points.map((p) => p.date ?? '');

    return {
      labels,
      datasets: [
        {
          label: 'CTL',
          data: points.map((p) => p.ctl ?? 0),
          borderColor: '#3f51b5',
          backgroundColor: 'rgba(63,81,181,0.08)',
          fill: true,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: 'ATL',
          data: points.map((p) => p.atl ?? 0),
          borderColor: '#ff9800',
          backgroundColor: 'rgba(255,152,0,0.08)',
          fill: true,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: 'TSB',
          data: points.map((p) => p.tsb ?? 0),
          borderColor: '#4caf50',
          backgroundColor: 'rgba(76,175,80,0.08)',
          fill: false,
          tension: 0.4,
          borderWidth: 2,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
      ],
    };
  }
}
