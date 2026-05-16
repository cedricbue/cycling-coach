import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { BikeFitActions } from '../+state/bike-fit.actions';
import { selectDetail, selectDetailError, selectDetailLoading } from '../+state/bike-fit.selectors';
import { VideoLandmarksPlayerComponent } from '../components/video-landmarks-player/video-landmarks-player.component';
import { AngleDisplayComponent, BikeAngle } from '../components/angle-display/angle-display.component';

const ALL_ANGLE_NAMES = ['Knee Angle', 'Hip Angle', 'Torso Angle', 'Elbow Angle', 'Ankle Angle'];

@Component({
  selector: 'app-bike-fit-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatProgressSpinnerModule,
    VideoLandmarksPlayerComponent,
    AngleDisplayComponent,
  ],
  templateUrl: './bike-fit-detail.component.html',
  styleUrl: './bike-fit-detail.component.scss',
})
export class BikeFitDetailComponent implements OnInit, OnDestroy {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly detail = this.store.selectSignal(selectDetail);
  readonly loading = this.store.selectSignal(selectDetailLoading);
  readonly error = this.store.selectSignal(selectDetailError);

  readonly angles = signal<BikeAngle[]>([]);
  readonly manualAngles = signal<number[]>([]);

  // Selecting an angle = showing it on video. Checkbox = select/deselect all.
  readonly selectedAngleNames = signal<Set<string>>(new Set(ALL_ANGLE_NAMES));
  readonly allSelected  = computed(() => this.selectedAngleNames().size === ALL_ANGLE_NAMES.length);
  readonly someSelected = computed(() => this.selectedAngleNames().size > 0 && !this.allSelected());

  videoUrl = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.videoUrl = `/api/bike-fit/analyses/${id}/video`;
    this.store.dispatch(BikeFitActions.loadAnalysisDetail({ id }));
  }

  ngOnDestroy(): void {
    this.store.dispatch(BikeFitActions.clearDetail());
  }

  goBack(): void { this.router.navigate(['/bike-fit']); }

  onAnglesChanged(angles: BikeAngle[]): void { this.angles.set(angles); }

  onAngleToggled(name: string): void {
    const next = new Set(this.selectedAngleNames());
    if (next.has(name)) next.delete(name); else next.add(name);
    this.selectedAngleNames.set(next);
  }

  toggleAll(checked: boolean): void {
    this.selectedAngleNames.set(checked ? new Set(ALL_ANGLE_NAMES) : new Set());
  }
}
