import {
  Component,
  ElementRef,
  input,
  OnDestroy,
  output,
  signal,
  ViewChild,
  AfterViewInit,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSliderModule } from '@angular/material/slider';
import { BikeAngle } from '../angle-display/angle-display.component';

interface Pt { x: number; y: number; z?: number; visibility?: number; }
interface FrameLandmarks { frameIndex: number; ts: number; landmarks: Pt[]; }
interface LandmarksReport {
  poseModel: string;
  poseSchema: string;
  fps: number;
  totalFrames: number;
  frames: FrameLandmarks[];
}

// MediaPipe (near-side: left body = indices 23/25/27/11/13/15/31)
const MP_CONNECTIONS = [
  [11,13],[13,15],[15,17],[15,19],[15,21],
  [12,14],[14,16],[16,18],[16,20],[16,22],
  [11,12],[23,24],[11,23],[12,24],
  [23,25],[25,27],[27,29],[27,31],[29,31],
  [24,26],[26,28],[28,30],[28,32],[30,32],
];

// COCO 17
const COCO_CONNECTIONS = [
  [5,7],[7,9],[6,8],[8,10],
  [5,6],[11,12],[5,11],[6,12],
  [11,13],[13,15],[12,14],[14,16],
];

// Halpe 26
const HALPE_CONNECTIONS = [
  ...COCO_CONNECTIONS,
  [15,17],[16,18],[15,19],[16,20],[15,21],[16,22],[17,18],
  [19,20],[21,22],[15,16],
];

function connectionsBySchema(schema: string): number[][] {
  if (schema.startsWith('coco')) return COCO_CONNECTIONS;
  if (schema.startsWith('halpe')) return HALPE_CONNECTIONS;
  return MP_CONNECTIONS;
}

function angleDeg(p1: Pt, vertex: Pt, p2: Pt): number {
  const v1 = { x: p1.x - vertex.x, y: p1.y - vertex.y };
  const v2 = { x: p2.x - vertex.x, y: p2.y - vertex.y };
  const dot = v1.x * v2.x + v1.y * v2.y;
  const mag = Math.hypot(v1.x, v1.y) * Math.hypot(v2.x, v2.y);
  if (mag === 0) return 0;
  return (Math.acos(Math.max(-1, Math.min(1, dot / mag))) * 180) / Math.PI;
}

function smoothed(arr: number[], radius = 3): number[] {
  return arr.map((_, i) => {
    const start = Math.max(0, i - radius);
    const end = Math.min(arr.length - 1, i + radius);
    const slice = arr.slice(start, end + 1);
    return slice.reduce((s, v) => s + v, 0) / slice.length;
  });
}

function localMaxima(arr: number[]): number[] {
  return arr
    .map((v, i) => (i > 0 && i < arr.length - 1 && v > arr[i - 1] && v > arr[i + 1] ? i : -1))
    .filter((i) => i >= 0);
}

function localMinima(arr: number[]): number[] {
  return arr
    .map((v, i) => (i > 0 && i < arr.length - 1 && v < arr[i - 1] && v < arr[i + 1] ? i : -1))
    .filter((i) => i >= 0);
}

interface FrameAngles {
  kneeExtension: number | null;
  kneeFlexion: number | null;
  hipAngle: number | null;
  torsoAngle: number;
  elbowAngle: number;
  ankleAngle: number | null;
}

@Component({
  selector: 'app-video-landmarks-player',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatSliderModule],
  templateUrl: './video-landmarks-player.component.html',
  styleUrl: './video-landmarks-player.component.scss',
})
export class VideoLandmarksPlayerComponent implements AfterViewInit, OnDestroy {
  @ViewChild('videoEl') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasRef!: ElementRef<HTMLCanvasElement>;

  readonly videoSrc = input<string>('');
  readonly landmarksJson = input<string | null>(null);
  readonly anglesChanged = output<BikeAngle[]>();

  report: LandmarksReport | null = null;
  measureMode = signal(false);
  private measurePoints: { x: number; y: number }[] = [];
  manualAngles = signal<number[]>([]);

  private rafId = 0;
  private avgAngles: FrameAngles | null = null;

  ngAfterViewInit(): void {
    const json = this.landmarksJson();
    if (json) {
      try {
        this.report = JSON.parse(json);
        this.computeAverageAngles();
      } catch {
        this.report = null;
      }
    }

    const video = this.videoRef.nativeElement;
    video.addEventListener('play', () => this.startRaf());
    video.addEventListener('pause', () => this.stopRaf());
    video.addEventListener('ended', () => this.stopRaf());
    video.addEventListener('seeked', () => this.drawFrame());
    video.addEventListener('loadedmetadata', () => {
      const canvas = this.canvasRef.nativeElement;
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
    });
  }

  ngOnDestroy(): void {
    this.stopRaf();
  }

  private startRaf(): void {
    const loop = () => {
      this.drawFrame();
      this.rafId = requestAnimationFrame(loop);
    };
    this.rafId = requestAnimationFrame(loop);
  }

  private stopRaf(): void {
    cancelAnimationFrame(this.rafId);
    this.drawFrame();
  }

  private drawFrame(): void {
    const video = this.videoRef?.nativeElement;
    const canvas = this.canvasRef?.nativeElement;
    if (!video || !canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (!this.report) return;

    const frame = this.findFrame(video.currentTime);
    if (!frame) return;

    const lm = frame.landmarks;
    const w = canvas.width;
    const h = canvas.height;
    const schema = this.report.poseSchema ?? '';
    const connections = connectionsBySchema(schema);

    ctx.strokeStyle = 'rgba(0,200,255,0.8)';
    ctx.lineWidth = 2;
    for (const [a, b] of connections) {
      const pa = lm[a];
      const pb = lm[b];
      if (!pa || !pb) continue;
      if ((pa.visibility ?? 1) < 0.3 || (pb.visibility ?? 1) < 0.3) continue;
      ctx.beginPath();
      ctx.moveTo(pa.x * w, pa.y * h);
      ctx.lineTo(pb.x * w, pb.y * h);
      ctx.stroke();
    }

    ctx.fillStyle = 'rgba(255,255,255,0.9)';
    for (const pt of lm) {
      if ((pt.visibility ?? 1) < 0.3) continue;
      ctx.beginPath();
      ctx.arc(pt.x * w, pt.y * h, 3, 0, Math.PI * 2);
      ctx.fill();
    }

    // manual measurement overlay
    ctx.strokeStyle = '#FFD600';
    ctx.lineWidth = 3;
    ctx.fillStyle = '#FFD600';
    for (let i = 0; i < this.measurePoints.length; i++) {
      const p = this.measurePoints[i];
      ctx.beginPath();
      ctx.arc(p.x * w, p.y * h, 5, 0, Math.PI * 2);
      ctx.fill();
      if (i > 0) {
        const prev = this.measurePoints[i - 1];
        ctx.beginPath();
        ctx.moveTo(prev.x * w, prev.y * h);
        ctx.lineTo(p.x * w, p.y * h);
        ctx.stroke();
      }
    }
  }

  private findFrame(currentTime: number): FrameLandmarks | null {
    if (!this.report?.frames.length) return null;
    const frames = this.report.frames;
    let lo = 0, hi = frames.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (frames[mid].ts <= currentTime) lo = mid; else hi = mid - 1;
    }
    return frames[lo];
  }

  private computeAverageAngles(): void {
    if (!this.report?.frames.length) return;
    const schema = this.report.poseSchema ?? '';
    const isMediapipe = schema.startsWith('mediapipe');

    // Keypoint indices for near side (left)
    const KP = isMediapipe
      ? { knee: 25, hip: 23, ankle: 27, shoulder: 11, elbow: 13, wrist: 15, foot: 31 }
      : { knee: 13, hip: 11, ankle: 15, shoulder: 5, elbow: 7, wrist: 9, foot: -1 };

    const frames = this.report.frames;

    // Compute raw knee.y per frame for BDC/TDC detection
    const kneeYArr = frames.map((f) => f.landmarks[KP.knee]?.y ?? 0);
    const kneeYSmooth = smoothed(kneeYArr, 3);
    const bdcIndices = new Set(localMaxima(kneeYSmooth));
    const tdcIndices = new Set(localMinima(kneeYSmooth));

    let kneeExtSum = 0, kneeExtCount = 0;
    let kneeFlexSum = 0, kneeFlexCount = 0;
    let hipSum = 0, hipCount = 0;
    let torsoSum = 0;
    let elbowSum = 0;
    let ankleSum = 0, ankleCount = 0;

    frames.forEach((f, i) => {
      const lm = f.landmarks;
      const get = (idx: number) => (idx >= 0 && lm[idx] && (lm[idx].visibility ?? 1) >= 0.3 ? lm[idx] : null);

      const knee = get(KP.knee);
      const hip = get(KP.hip);
      const ankle = get(KP.ankle);
      const shoulder = get(KP.shoulder);
      const elbow = get(KP.elbow);
      const wrist = get(KP.wrist);
      const foot = KP.foot >= 0 ? get(KP.foot) : null;

      if (knee && hip && ankle) {
        const kAngle = angleDeg(hip, knee, ankle);
        if (bdcIndices.has(i)) { kneeExtSum += kAngle; kneeExtCount++; }
        if (tdcIndices.has(i)) { kneeFlexSum += kAngle; kneeFlexCount++; }
      }
      if (hip && shoulder && knee && tdcIndices.has(i)) {
        hipSum += angleDeg(shoulder, hip, knee);
        hipCount++;
      }
      if (hip && shoulder) {
        torsoSum += angleDeg(shoulder, hip, { x: hip.x + 1, y: hip.y });
      }
      if (shoulder && elbow && wrist) {
        elbowSum += angleDeg(shoulder, elbow, wrist);
      }
      if (knee && ankle && foot && bdcIndices.has(i)) {
        ankleSum += angleDeg(knee, ankle, foot);
        ankleCount++;
      }
    });

    const n = frames.length;
    this.avgAngles = {
      kneeExtension: kneeExtCount ? Math.round(kneeExtSum / kneeExtCount) : null,
      kneeFlexion:   kneeFlexCount ? Math.round(kneeFlexSum / kneeFlexCount) : null,
      hipAngle:      hipCount ? Math.round(hipSum / hipCount) : null,
      torsoAngle:    Math.round(torsoSum / n),
      elbowAngle:    Math.round(elbowSum / n),
      ankleAngle:    ankleCount && isMediapipe ? Math.round(ankleSum / ankleCount) : null,
    };

    const bikeAngles: BikeAngle[] = [
      { name: 'Knee Extension', value: this.avgAngles.kneeExtension, min: 140, max: 150, unit: '°' },
      { name: 'Knee Flexion',   value: this.avgAngles.kneeFlexion,   min: 65,  max: 75,  unit: '°' },
      { name: 'Hip Angle',      value: this.avgAngles.hipAngle,      min: 45,  max: 60,  unit: '°' },
      { name: 'Torso Angle',    value: this.avgAngles.torsoAngle,    min: 40,  max: 50,  unit: '°' },
      { name: 'Elbow Angle',    value: this.avgAngles.elbowAngle,    min: 150, max: 165, unit: '°' },
      {
        name: 'Ankle (BDC)',
        value: this.avgAngles.ankleAngle,
        min: 90, max: 110, unit: '°',
        note: isMediapipe ? undefined : 'Requires MediaPipe (foot keypoint)',
      },
    ];

    this.anglesChanged.emit(bikeAngles);
  }

  onCanvasClick(event: MouseEvent): void {
    if (!this.measureMode()) return;
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    this.measurePoints.push({ x, y });

    if (this.measurePoints.length === 3) {
      const [p1, vertex, p2] = this.measurePoints;
      const angle = angleDeg(
        { x: p1.x, y: p1.y },
        { x: vertex.x, y: vertex.y },
        { x: p2.x, y: p2.y },
      );
      this.manualAngles.set([...this.manualAngles(), Math.round(angle * 10) / 10]);
      this.measurePoints = [];
    }
    this.drawFrame();
  }

  toggleMeasure(): void {
    this.measureMode.set(!this.measureMode());
    this.measurePoints = [];
    this.drawFrame();
  }

  clearManual(): void {
    this.manualAngles.set([]);
    this.measurePoints = [];
    this.drawFrame();
  }
}
