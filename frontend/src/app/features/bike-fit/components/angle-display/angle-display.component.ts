import { Component, input } from '@angular/core';
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

  angleClass = angleClass;
}
