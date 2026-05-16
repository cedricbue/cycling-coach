import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Store } from '@ngrx/store';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatChipsModule } from '@angular/material/chips';
import { BikeFitActions } from '../+state/bike-fit.actions';
import { selectAnalyses, selectListLoading, selectUploading } from '../+state/bike-fit.selectors';
import { UploadDialogComponent, UploadDialogResult } from '../components/upload-dialog/upload-dialog.component';

@Component({
  selector: 'app-bike-fit-list',
  standalone: true,
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTableModule,
    MatChipsModule,
  ],
  templateUrl: './bike-fit-list.component.html',
  styleUrl: './bike-fit-list.component.scss',
})
export class BikeFitListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  readonly analyses = this.store.selectSignal(selectAnalyses);
  readonly listLoading = this.store.selectSignal(selectListLoading);
  readonly uploading = this.store.selectSignal(selectUploading);

  readonly displayedColumns = ['createdAt', 'filename', 'poseModel', 'status', 'frames', 'actions'];

  ngOnInit(): void {
    this.store.dispatch(BikeFitActions.loadAnalyses());
  }

  openUpload(): void {
    const ref = this.dialog.open(UploadDialogComponent, { width: '420px' });
    ref.afterClosed().subscribe((result: UploadDialogResult | undefined) => {
      if (!result) return;
      this.store.dispatch(
        BikeFitActions.uploadAnalysis({
          video: result.file,
          filename: result.file.name,
          poseModel: result.poseModel,
          mediapipeComplexity: result.mediapipeComplexity,
          rtmposeMode: result.rtmposeMode,
          rtmposeSchema: result.rtmposeSchema,
          device: result.device,
        })
      );
    });
  }

  openDetail(id: string): void {
    this.router.navigate(['/bike-fit', id]);
  }

  statusColor(status: string): string {
    return status === 'DONE' ? 'primary' : status === 'FAILED' ? 'warn' : 'default';
  }
}
