import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { BikeFitActions } from '../+state/bike-fit.actions';
import { selectAnalyses, selectListLoading, selectUploading, selectUploadError } from '../+state/bike-fit.selectors';

@Component({
  selector: 'app-bike-fit-list',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './bike-fit-list.component.html',
  styleUrl: './bike-fit-list.component.scss',
})
export class BikeFitListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly router = inject(Router);

  readonly analyses = this.store.selectSignal(selectAnalyses);
  readonly listLoading = this.store.selectSignal(selectListLoading);
  readonly uploading = this.store.selectSignal(selectUploading);
  readonly uploadError = this.store.selectSignal(selectUploadError);

  readonly displayedColumns = ['createdAt', 'filename', 'poseModel', 'status', 'frames', 'actions'];

  // Upload form state
  selectedFile = signal<File | null>(null);
  dragOver = signal(false);
  poseModel = signal('rtmpose');
  mediapipeComplexity = signal(1);
  rtmposeMode = signal('balanced');
  rtmposeSchema = signal('halpe26');
  device = signal('auto');

  ngOnInit(): void {
    this.store.dispatch(BikeFitActions.loadAnalyses());
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.selectedFile.set(input.files[0]);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(true);
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(false);
    const file = event.dataTransfer?.files[0];
    if (file && file.type.startsWith('video/')) this.selectedFile.set(file);
  }

  submit(): void {
    const file = this.selectedFile();
    if (!file || this.uploading()) return;
    this.store.dispatch(
      BikeFitActions.uploadAnalysis({
        video: file,
        filename: file.name,
        poseModel: this.poseModel(),
        mediapipeComplexity: this.poseModel() === 'mediapipe' ? this.mediapipeComplexity() : undefined,
        rtmposeMode: this.poseModel() === 'rtmpose' ? this.rtmposeMode() : undefined,
        rtmposeSchema: this.poseModel() === 'rtmpose' ? this.rtmposeSchema() : undefined,
        device: this.device(),
      })
    );
    this.selectedFile.set(null);
  }

  retry(id: string): void {
    this.store.dispatch(BikeFitActions.retryAnalysis({ id }));
  }

  openDetail(id: string): void {
    this.router.navigate(['/bike-fit', id]);
  }

}
