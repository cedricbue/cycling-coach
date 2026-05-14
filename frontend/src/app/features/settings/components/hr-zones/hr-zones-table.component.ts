import { Component, input, computed } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { HrZoneSettings } from '../../../../core/api/model/models';

interface HrZoneRow {
  zone: string;
  name: string;
  description: string;
  minPct: number;
  maxPct: number | null;
  minBpm: number | null;
  maxBpm: number | null;
}

@Component({
  selector: 'app-hr-zones-table',
  imports: [MatTableModule],
  templateUrl: './hr-zones-table.component.html',
  styleUrl: './hr-zones-table.component.scss',
})
export class HrZonesTableComponent {
  readonly zones = input<HrZoneSettings | null>(null);
  readonly maxHr = input<number | null>(null);

  readonly displayedColumns = ['zone', 'name', 'pctRange', 'bpmRange', 'description'];

  readonly rows = computed<HrZoneRow[]>(() => {
    const z = this.zones();
    const maxHr = this.maxHr();

    const bounds = [
      { min: 0,              max: z?.z1Max ?? 60, zone: 'Z1', name: 'Recovery',  description: 'Very easy — active recovery, warm-up' },
      { min: z?.z1Max ?? 60, max: z?.z2Max ?? 72, zone: 'Z2', name: 'Endurance', description: 'Comfortable aerobic effort — long base rides' },
      { min: z?.z2Max ?? 72, max: z?.z3Max ?? 82, zone: 'Z3', name: 'Tempo',     description: 'Moderate intensity — builds aerobic capacity' },
      { min: z?.z3Max ?? 82, max: z?.z4Max ?? 92, zone: 'Z4', name: 'Threshold', description: 'Hard — lactate threshold and race pace' },
      { min: z?.z4Max ?? 92, max: 100,             zone: 'Z5', name: 'VO₂max',   description: 'Very hard — maximum aerobic effort' },
    ];

    return bounds.map((b) => ({
      zone: b.zone,
      name: b.name,
      description: b.description,
      minPct: b.min,
      maxPct: b.max,
      minBpm: maxHr ? Math.round((maxHr * b.min) / 100) : null,
      // Z5 upper bound is max HR itself — never show a number above it
      maxBpm: b.max && maxHr ? Math.min(Math.round((maxHr * b.max) / 100), maxHr) : null,
    }));
  });
}
