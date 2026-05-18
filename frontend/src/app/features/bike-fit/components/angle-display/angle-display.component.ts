import { Component, computed, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface BikeAngle {
  name: string;
  value: number | null;
  min: number;
  max: number;
  unit: string;
  note?: string;
}

export type RiderProfile = 'race' | 'endurance';

// Recommended ranges by rider profile (source: Retül / BikeFit Institute guidelines)
export const ANGLE_RANGES: Record<RiderProfile, Record<string, { min: number; max: number }>> = {
  race: {
    // Aggressive / aero position — closed hip, flat torso, race geometry
    'Knee Angle':  { min: 140, max: 150 },
    'Hip Angle':   { min: 45,  max: 58  },
    'Torso Angle': { min: 35,  max: 45  },
    'Elbow Angle': { min: 148, max: 162 },
    'Ankle Angle': { min: 90,  max: 110 },
  },
  endurance: {
    // Comfort / long-distance position — more open hip, upright torso
    'Knee Angle':  { min: 140, max: 150 },
    'Hip Angle':   { min: 55,  max: 70  },
    'Torso Angle': { min: 45,  max: 58  },
    'Elbow Angle': { min: 155, max: 170 },
    'Ankle Angle': { min: 90,  max: 110 },
  },
};

export const ANGLE_COLORS: Record<string, string> = {
  'Knee Angle':  '#00bcd4',
  'Hip Angle':   '#ab47bc',
  'Torso Angle': '#ff9800',
  'Elbow Angle': '#4caf50',
  'Ankle Angle': '#ef5350',
};

const CRANK_POSITIONS: Record<string, { label: string; tip: string }> = {
  'Knee Angle':  { label: 'BDC',    tip: 'Measured at Bottom Dead Center (6 o\'clock pedal)' },
  'Hip Angle':   { label: 'TDC',    tip: 'Measured at Top Dead Center (12 o\'clock pedal)' },
  'Torso Angle': { label: 'Static', tip: 'Static position — averaged across the pedal stroke' },
  'Elbow Angle': { label: 'Static', tip: 'Static position — averaged across the pedal stroke' },
  'Ankle Angle': { label: 'BDC',    tip: 'Dorsiflexion measured at Bottom Dead Center' },
};

@Component({
  selector: 'app-angle-display',
  standalone: true,
  imports: [CommonModule, MatTooltipModule],
  templateUrl: './angle-display.component.html',
  styleUrl: './angle-display.component.scss',
})
export class AngleDisplayComponent {
  readonly angles        = input<BikeAngle[]>([]);
  readonly manualMeasurements = input<number[]>([]);
  readonly selectedNames = input<Set<string>>(new Set());
  readonly riderProfile  = input<RiderProfile>('endurance');
  readonly nameToggled   = output<string>();

  // Effective min/max for a given angle name, considering the selected rider profile
  effectiveRange = (name: string): { min: number; max: number } =>
    ANGLE_RANGES[this.riderProfile()][name] ?? { min: 0, max: 180 };

  angleStatus = (angle: BikeAngle): string => {
    if (angle.value === null) return 'na';
    const { min, max } = this.effectiveRange(angle.name);
    if (angle.value >= min && angle.value <= max) return 'good';
    const delta = angle.value < min ? min - angle.value : angle.value - max;
    return delta <= 5 ? 'warning' : 'bad';
  };

  angleColor = (name: string) => ANGLE_COLORS[name] ?? '#888';
  crankPos   = (name: string) => CRANK_POSITIONS[name] ?? { label: '', tip: '' };
}
