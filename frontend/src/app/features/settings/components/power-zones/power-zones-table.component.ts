import { Component, input, computed } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { PowerZoneSettings } from '../../../../core/api/model/models';

interface ZoneRow {
  zone: string;
  name: string;
  description: string;
  minPct: number;
  maxPct: number | null;
  minW: number | null;
  maxW: number | null;
}

@Component({
  selector: 'app-power-zones-table',
  imports: [MatTableModule],
  templateUrl: './power-zones-table.component.html',
  styleUrl: './power-zones-table.component.scss',
})
export class PowerZonesTableComponent {
  readonly zones = input<PowerZoneSettings | null>(null);
  readonly ftp = input<number | null>(null);

  readonly displayedColumns = ['zone', 'name', 'pctRange', 'wattRange', 'description'];

  readonly rows = computed<ZoneRow[]>(() => {
    const z = this.zones();
    const ftp = this.ftp();

    const bounds = [
      { min: 0, max: z?.z1Max ?? 55, zone: 'Z1', name: 'Active Recovery', description: 'Easy spinning, recovery rides' },
      { min: z?.z1Max ?? 55, max: z?.z2Max ?? 75, zone: 'Z2', name: 'Endurance', description: 'Long aerobic rides, fat-burning base' },
      { min: z?.z2Max ?? 75, max: z?.z3Max ?? 90, zone: 'Z3', name: 'Tempo', description: 'Comfortably hard — builds aerobic power' },
      { min: z?.z3Max ?? 90, max: z?.z4Max ?? 105, zone: 'Z4', name: 'Lactate Threshold', description: 'Race pace, threshold efforts' },
      { min: z?.z4Max ?? 105, max: z?.z5Max ?? 120, zone: 'Z5', name: 'VO₂max', description: 'Short hard intervals, max aerobic capacity' },
      { min: z?.z5Max ?? 120, max: 150, zone: 'Z6', name: 'Anaerobic', description: 'Short all-out efforts > 1 min' },
      { min: 150, max: null, zone: 'Z7', name: 'Neuromuscular', description: 'Sprint power, < 30 sec maximal efforts' },
    ];

    return bounds.map((b) => ({
      zone: b.zone,
      name: b.name,
      description: b.description,
      minPct: b.min,
      maxPct: b.max,
      minW: ftp ? Math.round((ftp * b.min) / 100) : null,
      maxW: b.max && ftp ? Math.round((ftp * b.max) / 100) : null,
    }));
  });
}
