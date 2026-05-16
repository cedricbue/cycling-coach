import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';

export interface UploadDialogResult {
  file: File;
  poseModel: string;
  mediapipeComplexity?: number;
  rtmposeMode?: string;
  rtmposeSchema?: string;
  device: string;
}

@Component({
  selector: 'app-upload-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
  ],
  templateUrl: './upload-dialog.component.html',
  styleUrl: './upload-dialog.component.scss',
})
export class UploadDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<UploadDialogComponent>);

  selectedFile = signal<File | null>(null);
  poseModel = signal('mediapipe');
  mediapipeComplexity = signal(1);
  device = signal('auto');
  dragOver = signal(false);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.selectedFile.set(input.files[0]);
    }
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
    if (file && file.type.startsWith('video/')) {
      this.selectedFile.set(file);
    }
  }

  submit(): void {
    const file = this.selectedFile();
    if (!file) return;
    const result: UploadDialogResult = {
      file,
      poseModel: this.poseModel(),
      device: this.device(),
    };
    if (this.poseModel() === 'mediapipe') {
      result.mediapipeComplexity = this.mediapipeComplexity();
    }
    this.dialogRef.close(result);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
