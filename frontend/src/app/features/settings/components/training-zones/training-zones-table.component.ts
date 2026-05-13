import { Component, input, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { PowerZoneSettings, HrZoneSettings } from '../../../../core/api/model/models';

interface ZoneRow {
  zone: string;
  name: string;
  description: string;
  powerMinPct: number;
  powerMaxPct: number | null;
  minW: number | null;
  maxW: number | null;
  hrMinPct: number | null;
  hrMaxPct: number | null;
  minBpm: number | null;
  maxBpm: number | null;
}

@Component({
  selector: 'app-training-zones-table',
  imports: [DecimalPipe, MatTableModule],
  templateUrl: './training-zones-table.component.html',
  styleUrl: './training-zones-table.component.scss',
})
export class TrainingZonesTableComponent {
  readonly powerZones = input<PowerZoneSettings | null>(null);
  readonly hrZones = input<HrZoneSettings | null>(null);
  readonly ftp = input<number | null>(null);
  readonly maxHr = input<number | null>(null);

  readonly displayedColumns = ['zone', 'name', 'watts', 'bpm', 'description'];

  readonly rows = computed<ZoneRow[]>(() => {
    const p = this.powerZones();
    const h = this.hrZones();
    const ftp = this.ftp();
    const maxHr = this.maxHr();

    // HR zone upper bounds (null = no HR guidance at this intensity)
    const hrBounds: [number, number | null][] = [
      [0,               h?.z1Max ?? 68],
      [h?.z1Max ?? 68,  h?.z2Max ?? 83],
      [h?.z2Max ?? 83,  h?.z3Max ?? 94],
      [h?.z3Max ?? 94,  h?.z4Max ?? 105],
      [h?.z4Max ?? 105, null],
      [0, null], // Z6 — HR unreliable at anaerobic intensities
      [0, null], // Z7 — HR unreliable at neuromuscular intensities
    ];

    const powerBounds = [
      { min: 0,               max: p?.z1Max ?? 55,  zone: 'Z1', name: 'Active Recovery',    description: 'Easy spinning, recovery rides' },
      { min: p?.z1Max ?? 55,  max: p?.z2Max ?? 75,  zone: 'Z2', name: 'Endurance',           description: 'Long aerobic rides, fat-burning base' },
      { min: p?.z2Max ?? 75,  max: p?.z3Max ?? 90,  zone: 'Z3', name: 'Tempo',               description: 'Comfortably hard — builds aerobic power' },
      { min: p?.z3Max ?? 90,  max: p?.z4Max ?? 105, zone: 'Z4', name: 'Lactate Threshold',   description: 'Race pace, threshold efforts' },
      { min: p?.z4Max ?? 105, max: p?.z5Max ?? 120, zone: 'Z5', name: 'VO₂max',              description: 'Short hard intervals, max aerobic capacity' },
      { min: p?.z5Max ?? 120, max: 150,              zone: 'Z6', name: 'Anaerobic',           description: 'Short all-out efforts > 1 min — HR lags' },
      { min: 150,             max: null,             zone: 'Z7', name: 'Neuromuscular',       description: 'Sprint power, < 30 sec — HR not meaningful' },
    ];

    return powerBounds.map((b, i) => {
      const [hrMin, hrMax] = hrBounds[i];
      const hasHr = i < 5; // Z6 and Z7 have no HR guidance
      return {
        zone: b.zone,
        name: b.name,
        description: b.description,
        powerMinPct: b.min,
        powerMaxPct: b.max,
        minW: ftp ? Math.round((ftp * b.min) / 100) : null,
        maxW: b.max && ftp ? Math.round((ftp * b.max) / 100) : null,
        hrMinPct: hasHr ? hrMin : null,
        hrMaxPct: hasHr ? hrMax : null,
        minBpm: hasHr && maxHr ? Math.round((maxHr * hrMin) / 100) : null,
        maxBpm: hasHr && hrMax && maxHr ? Math.round((maxHr * hrMax) / 100) : null,
      };
    });
  });
}
