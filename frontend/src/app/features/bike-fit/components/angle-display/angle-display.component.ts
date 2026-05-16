import { Component, input, output } from '@angular/core';
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

export const ANGLE_COLORS: Record<string, string> = {
  'Knee Angle':  '#00bcd4',
  'Hip Angle':   '#ab47bc',
  'Torso Angle': '#ff9800',
  'Elbow Angle': '#4caf50',
  'Ankle Angle': '#ef5350',
};

// When in the pedal stroke this angle is most relevant for bike fitting
const CRANK_POSITIONS: Record<string, { label: string; tip: string }> = {
  'Knee Angle':  { label: 'BDC',    tip: 'Measured at Bottom Dead Center (6 o\'clock pedal)' },
  'Hip Angle':   { label: 'TDC',    tip: 'Measured at Top Dead Center (12 o\'clock pedal)' },
  'Torso Angle': { label: 'Static', tip: 'Static position — averaged across the pedal stroke' },
  'Elbow Angle': { label: 'Static', tip: 'Static position — averaged across the pedal stroke' },
  'Ankle Angle': { label: 'BDC',    tip: 'Dorsiflexion measured at Bottom Dead Center' },
};

function angleClass(angle: BikeAngle): string {
  if (angle.value === null) return 'na';
  if (angle.value >= angle.min && angle.value <= angle.max) return 'good';
  const delta = angle.value < angle.min ? angle.min - angle.value : angle.value - angle.max;
  return delta <= 5 ? 'warning' : 'bad';
}

@Component({
  selector: 'app-angle-display',
  standalone: true,
  imports: [CommonModule, MatTooltipModule],
  templateUrl: './angle-display.component.html',
  styleUrl: './angle-display.component.scss',
})
export class AngleDisplayComponent {
  readonly angles = input<BikeAngle[]>([]);
  readonly manualMeasurements = input<number[]>([]);
  readonly selectedNames = input<Set<string>>(new Set());
  readonly nameToggled = output<string>();

  angleClass = angleClass;
  angleColor = (name: string) => ANGLE_COLORS[name] ?? '#888';
  crankPos   = (name: string) => CRANK_POSITIONS[name] ?? { label: '', tip: '' };
}
