import {
  Component,
  effect,
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
import { MatTooltipModule } from '@angular/material/tooltip';
import { ANGLE_COLORS, BikeAngle } from '../angle-display/angle-display.component';

interface Pt { x: number; y: number; z?: number; visibility?: number; }
// lm entry: [x, y, z, score] normalised image coordinates
interface FrameLandmarks { fi: number; ts: number; lm: number[][]; }
interface LandmarksReport {
  pose_model: string;
  schema: string;
  fps: number;
  total_frames: number;
  frames_landmarks: FrameLandmarks[];
}

function lmToPt(entry: number[]): Pt {
  return { x: entry[0], y: entry[1], z: entry[2], visibility: entry[3] };
}

function avgLmScore(frames: FrameLandmarks[], indices: number[]): number {
  let sum = 0, count = 0;
  for (const f of frames) {
    for (const idx of indices) {
      const p = f.lm[idx];
      if (p && p.length >= 4) { sum += p[3]; count++; }
    }
  }
  return count ? sum / count : 0;
}

function torsoAngleDeg(shoulder: Pt, hip: Pt): number {
  const dx = Math.abs(shoulder.x - hip.x);
  const dy = Math.abs(shoulder.y - hip.y);
  return Math.atan2(dy, dx) * 180 / Math.PI;
}

// MediaPipe 33 keypoints:
// 11=L_shoulder 12=R_shoulder 13=L_elbow 14=R_elbow 15=L_wrist 16=R_wrist
// 23=L_hip 24=R_hip 25=L_knee 26=R_knee 27=L_ankle 28=R_ankle
// 29=L_heel 30=R_heel 31=L_foot_index 32=R_foot_index
const MP_CONNECTIONS = [
  // arms
  [11,13],[13,15],
  [12,14],[14,16],
  // torso
  [11,12],[23,24],[11,23],[12,24],
  // legs
  [23,25],[25,27],
  [24,26],[26,28],
  // feet
  [27,29],[27,31],[29,31],
  [28,30],[28,32],[30,32],
];

// COCO 17 keypoints:
// 5=L_shoulder 6=R_shoulder 7=L_elbow 8=R_elbow 9=L_wrist 10=R_wrist
// 11=L_hip 12=R_hip 13=L_knee 14=R_knee 15=L_ankle 16=R_ankle
const COCO_CONNECTIONS = [
  // arms
  [5,7],[7,9],
  [6,8],[8,10],
  // torso
  [5,6],[11,12],[5,11],[6,12],
  // legs
  [11,13],[13,15],
  [12,14],[14,16],
];

// Halpe 26 = COCO 17 + extra:
// 17=head_top 18=neck 19=hip_center
// 20=L_big_toe 21=R_big_toe 22=L_small_toe 23=R_small_toe 24=L_heel 25=R_heel
const HALPE_CONNECTIONS = [
  ...COCO_CONNECTIONS,
  // head / spine
  [17,18],[18,0],        // head_top → neck → nose
  [18,5],[18,6],         // neck → shoulders
  [18,19],[19,11],[19,12], // neck → hip_center → hips
  // left foot
  [15,20],[15,22],[15,24],[20,22],[22,24],
  // right foot
  [16,21],[16,23],[16,25],[21,23],[23,25],
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

interface KpConfig {
  knee: number; hip: number; ankle: number;
  shoulder: number; elbow: number; wrist: number; foot: number;
}

// Measurement system
interface MeasurePoint { x: number; y: number; landmarkIdx: number | null; }
interface Measurement { p1: MeasurePoint; vertex: MeasurePoint; p2: MeasurePoint; }

const SNAP_PX = 22; // snap radius in screen pixels

@Component({
  selector: 'app-video-landmarks-player',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule, MatSliderModule, MatTooltipModule],
  templateUrl: './video-landmarks-player.component.html',
  styleUrl: './video-landmarks-player.component.scss',
})
export class VideoLandmarksPlayerComponent implements AfterViewInit, OnDestroy {
  @ViewChild('videoEl') videoRef!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasEl') canvasRef!: ElementRef<HTMLCanvasElement>;

  readonly videoSrc = input<string>('');
  readonly landmarksJson = input<string | null>(null);
  readonly visibleAngleNames = input<Set<string>>(new Set());
  readonly anglesChanged = output<BikeAngle[]>();

  constructor() {
    // Redraw immediately when the angle selection changes (while video is paused)
    effect(() => {
      this.visibleAngleNames();
      this.drawFrame();
    });
  }

  report: LandmarksReport | null = null;
  measureMode    = signal(false);
  hasMeasurements = signal(false);
  playbackRate   = signal(1);

  readonly speeds = [
    { rate: 0.5,  label: '0.5x' },
    { rate: 0.75, label: '0.75x' },
    { rate: 1,    label: '1x' },
  ];

  private measurements: Measurement[] = [];
  private activeDrag: { mIdx: number; pt: 'p1' | 'vertex' | 'p2' } | null = null;
  private cursorNorm: { x: number; y: number } | null = null;

  private rafId = 0;
  private kp: KpConfig | null = null;
  private schema = '';
  private poseModel = '';
  private isMediapipe = false;
  private resizeObs!: ResizeObserver;

  private readonly keyHandler = (e: KeyboardEvent) => {
    // Don't intercept when focus is inside an input or editable element
    const tag = (e.target as HTMLElement).tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || (e.target as HTMLElement).isContentEditable) return;

    const video = this.videoRef?.nativeElement;
    if (!video) return;

    if (e.code === 'Space') {
      e.preventDefault();
      video.paused ? video.play() : video.pause();
    } else if (e.code === 'ArrowLeft') {
      e.preventDefault();
      const frameDur = 1 / (this.report?.fps ?? 30);
      video.currentTime = Math.max(0, video.currentTime - frameDur);
    } else if (e.code === 'ArrowRight') {
      e.preventDefault();
      const frameDur = 1 / (this.report?.fps ?? 30);
      video.currentTime = Math.min(video.duration ?? Infinity, video.currentTime + frameDur);
    }
  };

  ngAfterViewInit(): void {
    const json = this.landmarksJson();
    if (json) {
      try {
        this.report = JSON.parse(json);
        this.schema    = this.report?.schema     ?? '';
        this.poseModel = this.report?.pose_model ?? '';
        this.isMediapipe = this.schema.startsWith('mediapipe');
        this.computeAverageAngles();
        this.drawFrame();
      } catch {
        this.report = null;
      }
    }

    const video = this.videoRef.nativeElement;
    const canvas = this.canvasRef.nativeElement;
    video.addEventListener('play', () => this.startRaf());
    video.addEventListener('pause', () => this.stopRaf());
    video.addEventListener('ended', () => this.stopRaf());
    video.addEventListener('seeked', () => this.drawFrame());
    video.addEventListener('loadedmetadata', () => {
      canvas.width = video.videoWidth;
      canvas.height = video.videoHeight;
      this.repositionCanvas();
    });
    video.addEventListener('loadeddata', () => this.drawFrame());

    // Observe cc-page (stable, unaffected by video sizing).
    // DOM: canvas → video-container → player-wrapper → host → video-column → detail-layout → cc-page
    const ccPage = canvas.parentElement!.parentElement!.parentElement!
      .parentElement!.parentElement!.parentElement!;
    this.resizeObs = new ResizeObserver(() => this.repositionCanvas());
    this.resizeObs.observe(ccPage);
    canvas.addEventListener('mousedown', (e) => this.onCanvasMouseDown(e));
    canvas.addEventListener('mousemove', (e) => this.onCanvasMouseMove(e));
    canvas.addEventListener('mouseup',   (e) => this.onCanvasMouseUp(e));
    canvas.addEventListener('mouseleave', () => { this.cursorNorm = null; this.activeDrag = null; this.drawFrame(); });
    // end drag when mouse released anywhere outside canvas
    document.addEventListener('mouseup',  () => { this.activeDrag = null; });
    document.addEventListener('keydown', this.keyHandler);
  }

  ngOnDestroy(): void {
    this.stopRaf();
    this.resizeObs?.disconnect();
    document.removeEventListener('keydown', this.keyHandler);
  }

  setSpeed(rate: number): void {
    this.playbackRate.set(rate);
    const video = this.videoRef?.nativeElement;
    if (video) video.playbackRate = rate;
  }

  // Size the container to exactly match the video's native AR, bounded by the
  // available column width and 75 % of the viewport height.
  // The canvas (inset: 0, 100%×100%) then covers the container with no letterboxing.
  private repositionCanvas(): void {
    const video = this.videoRef?.nativeElement;
    const canvas = this.canvasRef?.nativeElement;
    if (!video || !canvas || !video.videoWidth || !video.videoHeight) return;

    const container = canvas.parentElement as HTMLElement; // video-container
    // Use cc-page width (stable): player-wrapper → host → video-column → detail-layout → cc-page
    const ccPage = container.parentElement?.parentElement?.parentElement
      ?.parentElement?.parentElement as HTMLElement | undefined;
    const pageW = ccPage?.clientWidth ?? window.innerWidth;
    // Subtract the fixed right panel (360px) + gap (16px)
    const maxW = Math.max(pageW - 376, 200);
    const maxH = window.innerHeight * 0.85;

    const videoAR = video.videoWidth / video.videoHeight;
    const h = Math.min(maxW / videoAR, maxH);
    const w = Math.round(h * videoAR);

    container.style.width  = `${w}px`;
    container.style.height = `${Math.round(h)}px`;
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

    this.emitFrameAngles(frame);

    const rawLm = frame.lm;
    const w = canvas.width;
    const h = canvas.height;
    const connections = connectionsBySchema(this.schema);

    ctx.strokeStyle = 'rgba(0,200,255,0.8)';
    ctx.lineWidth = 2;
    for (const [a, b] of connections) {
      const pa = rawLm[a];
      const pb = rawLm[b];
      if (!pa || !pb) continue;
      if ((pa[3] ?? 1) < 0.3 || (pb[3] ?? 1) < 0.3) continue;
      ctx.beginPath();
      ctx.moveTo(pa[0] * w, pa[1] * h);
      ctx.lineTo(pb[0] * w, pb[1] * h);
      ctx.stroke();
    }

    ctx.fillStyle = 'rgba(255,255,255,0.9)';
    for (const pt of rawLm) {
      if ((pt[3] ?? 1) < 0.3) continue;
      ctx.beginPath();
      ctx.arc(pt[0] * w, pt[1] * h, 5, 0, Math.PI * 2);
      ctx.fill();
    }

    this.drawAngleOverlays(ctx, w, h, frame);
    this.drawMeasurements(ctx, w, h, frame);
  }

  private findFrame(currentTime: number): FrameLandmarks | null {
    if (!this.report?.frames_landmarks.length) return null;
    const frames = this.report.frames_landmarks;
    let lo = 0, hi = frames.length - 1;
    while (lo < hi) {
      const mid = (lo + hi + 1) >> 1;
      if (frames[mid].ts <= currentTime) lo = mid; else hi = mid - 1;
    }
    return frames[lo];
  }

  private computeAverageAngles(): void {
    if (!this.report?.frames_landmarks.length) return;
    // this.schema / this.poseModel / this.isMediapipe already set in ngAfterViewInit

    const frames = this.report.frames_landmarks;

    // Auto-detect near side by comparing avg confidence of left vs right lower limbs
    if (this.isMediapipe) {
      const leftScore  = avgLmScore(frames, [23, 25, 27]);
      const rightScore = avgLmScore(frames, [24, 26, 28]);
      this.kp = rightScore > leftScore
        ? { knee: 26, hip: 24, ankle: 28, shoulder: 12, elbow: 14, wrist: 16, foot: 32 }
        : { knee: 25, hip: 23, ankle: 27, shoulder: 11, elbow: 13, wrist: 15, foot: 31 };
    } else {
      const leftScore  = avgLmScore(frames, [11, 13, 15]);
      const rightScore = avgLmScore(frames, [12, 14, 16]);
      this.kp = rightScore > leftScore
        ? { knee: 14, hip: 12, ankle: 16, shoulder: 6, elbow: 8, wrist: 10, foot: -1 }
        : { knee: 13, hip: 11, ankle: 15, shoulder: 5, elbow: 7, wrist: 9,  foot: -1 };
    }
    const KP = this.kp;

    // Compute raw knee.y per frame for BDC/TDC detection
    const kneeYArr = frames.map((f) => f.lm[KP.knee]?.[1] ?? 0);
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
      const get = (idx: number): Pt | null => {
        const p = f.lm[idx];
        return (idx >= 0 && p && (p[3] ?? 1) >= 0.3) ? lmToPt(p) : null;
      };

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
        torsoSum += torsoAngleDeg(shoulder, hip);
      }
      if (shoulder && elbow && wrist) {
        elbowSum += angleDeg(shoulder, elbow, wrist);
      }
      if (knee && ankle && foot && bdcIndices.has(i)) {
        ankleSum += angleDeg(knee, ankle, foot);
        ankleCount++;
      }
    });

  }

  private emitFrameAngles(frame: FrameLandmarks): void {
    const kp = this.kp;
    if (!kp) return;

    // Use pixel dimensions so angles match the visual overlay exactly
    const canvas = this.canvasRef?.nativeElement;
    const w = canvas?.width  ?? 1;
    const h = canvas?.height ?? 1;

    const get = (idx: number): Pt | null => {
      const p = frame.lm[idx];
      return (idx >= 0 && p && (p[3] ?? 1) >= 0.3) ? lmToPt(p) : null;
    };

    // Angle between three points computed in pixel space (accounts for video AR)
    const px = (p1: Pt, v: Pt, p2: Pt): number => {
      const dx1 = (p1.x - v.x) * w, dy1 = (p1.y - v.y) * h;
      const dx2 = (p2.x - v.x) * w, dy2 = (p2.y - v.y) * h;
      const dot = dx1 * dx2 + dy1 * dy2;
      const mag = Math.hypot(dx1, dy1) * Math.hypot(dx2, dy2);
      return mag === 0 ? 0 : Math.acos(Math.max(-1, Math.min(1, dot / mag))) * 180 / Math.PI;
    };

    // Torso angle from horizontal in pixel space
    const torsoPx = (shoulder: Pt, hip: Pt): number => {
      const dx = Math.abs((shoulder.x - hip.x) * w);
      const dy = Math.abs((shoulder.y - hip.y) * h);
      return Math.atan2(dy, dx) * 180 / Math.PI;
    };

    const knee     = get(kp.knee);
    const hip      = get(kp.hip);
    const ankle    = get(kp.ankle);
    const shoulder = get(kp.shoulder);
    const elbow    = get(kp.elbow);
    const wrist    = get(kp.wrist);
    const foot     = kp.foot >= 0 ? get(kp.foot) : null;

    this.anglesChanged.emit([
      { name: 'Knee Angle',  value: knee && hip && ankle       ? Math.round(px(hip, knee, ankle))        : null, min: 140, max: 150, unit: '°' },
      { name: 'Hip Angle',   value: hip && shoulder && knee    ? Math.round(px(shoulder, hip, knee))     : null, min: 45,  max: 60,  unit: '°' },
      { name: 'Torso Angle', value: hip && shoulder            ? Math.round(torsoPx(shoulder, hip))      : null, min: 40,  max: 50,  unit: '°' },
      { name: 'Elbow Angle', value: shoulder && elbow && wrist ? Math.round(px(shoulder, elbow, wrist))  : null, min: 150, max: 165, unit: '°' },
      {
        name: 'Ankle Angle',
        value: this.isMediapipe && knee && ankle && foot ? Math.round(px(knee, ankle, foot)) : null,
        min: 90, max: 110, unit: '°',
        note: this.isMediapipe ? undefined : 'Requires MediaPipe',
      },
    ]);
  }

  // ── angle overlays ────────────────────────────────────────────────────────

  private drawAngleOverlays(ctx: CanvasRenderingContext2D, w: number, h: number, frame: FrameLandmarks): void {
    const kp = this.kp;
    const visible = this.visibleAngleNames();
    if (!kp || !visible.size) return;

    const get = (idx: number): { x: number; y: number } | null => {
      if (idx < 0) return null;
      const p = frame.lm[idx];
      return p && (p[3] ?? 1) >= 0.3 ? { x: p[0], y: p[1] } : null;
    };

    const knee     = get(kp.knee);
    const hip      = get(kp.hip);
    const ankle    = get(kp.ankle);
    const shoulder = get(kp.shoulder);
    const elbow    = get(kp.elbow);
    const wrist    = get(kp.wrist);
    const foot     = kp.foot >= 0 ? get(kp.foot) : null;

    if (visible.has('Knee Angle') && knee && hip && ankle)
      this.drawAngleArc(ctx, hip, knee, ankle, w, h, ANGLE_COLORS['Knee Angle']);

    if (visible.has('Hip Angle') && hip && shoulder && knee)
      this.drawAngleArc(ctx, shoulder, hip, knee, w, h, ANGLE_COLORS['Hip Angle']);

    if (visible.has('Torso Angle') && hip && shoulder) {
      // Reference goes the same direction as the shoulder (toward the cyclist's front)
      // so the arc shows the angle between torso and forward-horizontal, not its supplement
      const refX = shoulder.x < hip.x ? hip.x - 0.12 : hip.x + 0.12;
      this.drawAngleArc(ctx, shoulder, hip, { x: refX, y: hip.y }, w, h, ANGLE_COLORS['Torso Angle'], true);
    }

    if (visible.has('Elbow Angle') && shoulder && elbow && wrist)
      this.drawAngleArc(ctx, shoulder, elbow, wrist, w, h, ANGLE_COLORS['Elbow Angle']);

    if (visible.has('Ankle Angle') && this.isMediapipe && knee && ankle && foot)
      this.drawAngleArc(ctx, knee, ankle, foot, w, h, ANGLE_COLORS['Ankle Angle']);
  }

  private drawAngleArc(
    ctx: CanvasRenderingContext2D,
    arm1: { x: number; y: number },
    vertex: { x: number; y: number },
    arm2: { x: number; y: number },
    w: number, h: number,
    color: string,
    dottedSecondArm = false,
  ): void {
    const vx = vertex.x * w, vy = vertex.y * h;
    const a1x = arm1.x * w, a1y = arm1.y * h;
    const a2x = arm2.x * w, a2y = arm2.y * h;

    ctx.save();
    ctx.strokeStyle = color;
    ctx.lineWidth = 2;

    // First arm: solid
    ctx.beginPath(); ctx.moveTo(vx, vy); ctx.lineTo(a1x, a1y); ctx.stroke();
    // Second arm: solid or dotted (reference line)
    if (dottedSecondArm) ctx.setLineDash([5, 4]);
    ctx.beginPath(); ctx.moveTo(vx, vy); ctx.lineTo(a2x, a2y); ctx.stroke();
    ctx.setLineDash([]);

    // Arc
    const ang1 = Math.atan2(a1y - vy, a1x - vx);
    const ang2 = Math.atan2(a2y - vy, a2x - vx);
    const d1 = Math.hypot(a1x - vx, a1y - vy);
    const d2 = Math.hypot(a2x - vx, a2y - vy);
    const arcR = Math.min(28, Math.min(d1, d2) * 0.38);
    if (arcR >= 3) {
      const cwSpan = ((ang2 - ang1) + 2 * Math.PI) % (2 * Math.PI);
      const ccw = cwSpan > Math.PI;
      const midA = ccw ? ang1 - (2 * Math.PI - cwSpan) / 2 : ang1 + cwSpan / 2;
      const angleDegVal = Math.acos(Math.max(-1, Math.min(1,
        ((a1x-vx)*(a2x-vx) + (a1y-vy)*(a2y-vy)) / (d1*d2)
      ))) * 180 / Math.PI;

      ctx.beginPath();
      ctx.moveTo(vx, vy);
      ctx.arc(vx, vy, arcR, ang1, ang2, ccw);
      ctx.closePath();
      ctx.globalAlpha = 0.3;
      ctx.fillStyle = color;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.stroke();

      // Label
      const lx = vx + Math.cos(midA) * (arcR + 14);
      const ly = vy + Math.sin(midA) * (arcR + 14);
      ctx.font = 'bold 16px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.strokeStyle = 'rgba(255,255,255,0.85)';
      ctx.lineWidth = 3;
      ctx.strokeText(`${Math.round(angleDegVal)}°`, lx, ly);
      ctx.fillStyle = color;
      ctx.fillText(`${Math.round(angleDegVal)}°`, lx, ly);
    }
    ctx.restore();
  }

  // ── measurement interaction ───────────────────────────────────────────────

  toggleMeasure(): void {
    if (this.measureMode()) {
      this.measureMode.set(false);
    } else {
      this.measureMode.set(true);
      this.addMeasurement();
    }
    this.drawFrame();
  }

  addMeasurement(): void {
    this.measurements.push({
      p1:     { x: 0.33, y: 0.28, landmarkIdx: null },
      vertex: { x: 0.33, y: 0.58, landmarkIdx: null },
      p2:     { x: 0.63, y: 0.58, landmarkIdx: null },
    });
    this.hasMeasurements.set(true);
    this.drawFrame();
  }

  clearMeasurements(): void {
    this.measurements = [];
    this.hasMeasurements.set(false);
    this.measureMode.set(false);
    this.activeDrag = null;
    this.drawFrame();
  }

  onCanvasMouseDown(event: MouseEvent): void {
    if (!this.measureMode()) return;
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    const frame = this.findFrame(this.videoRef.nativeElement.currentTime);

    let bestDist = Infinity;
    let bestDrag: typeof this.activeDrag = null;
    for (let mIdx = 0; mIdx < this.measurements.length; mIdx++) {
      const m = this.measurements[mIdx];
      for (const pt of ['p1', 'vertex', 'p2'] as const) {
        const pos = this.resolvedPt(m[pt], frame);
        const dx = (pos.x - x) * rect.width;
        const dy = (pos.y - y) * rect.height;
        const d = Math.hypot(dx, dy);
        if (d < SNAP_PX * 1.5 && d < bestDist) { bestDist = d; bestDrag = { mIdx, pt }; }
      }
    }
    if (bestDrag) { this.activeDrag = bestDrag; event.preventDefault(); }
  }

  onCanvasMouseMove(event: MouseEvent): void {
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    this.cursorNorm = { x, y };

    if (this.activeDrag && this.measureMode()) {
      const frame = this.findFrame(this.videoRef.nativeElement.currentTime);
      const { mIdx, pt } = this.activeDrag;
      const m = this.measurements[mIdx];
      this.measurements[mIdx] = { ...m, [pt]: this.snapPoint(x, y, event, frame, rect, m, pt) };
    }

    // Update cursor style
    const isOverPoint = this.measurements.some(m => {
      const frame = this.findFrame(this.videoRef.nativeElement.currentTime);
      return ['p1', 'vertex', 'p2'].some(pt => {
        const pos = this.resolvedPt(m[pt as 'p1'|'vertex'|'p2'], frame);
        return Math.hypot((pos.x - x) * rect.width, (pos.y - y) * rect.height) < SNAP_PX * 1.5;
      });
    });
    canvas.style.cursor = this.activeDrag ? 'grabbing' : isOverPoint ? 'grab' : (this.measureMode() ? 'default' : '');

    if (this.measureMode() || this.measurements.length) this.drawFrame();
  }

  onCanvasMouseUp(event: MouseEvent): void {
    if (!this.activeDrag) return;
    // On release, try to snap to nearest landmark
    const canvas = this.canvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width;
    const y = (event.clientY - rect.top) / rect.height;
    const frame = this.findFrame(this.videoRef.nativeElement.currentTime);
    const { mIdx, pt } = this.activeDrag;
    const m = this.measurements[mIdx];
    this.measurements[mIdx] = { ...m, [pt]: this.snapPoint(x, y, event, frame, rect, m, pt) };
    this.activeDrag = null;
    this.drawFrame();
  }

  // ── snapping ──────────────────────────────────────────────────────────────

  private snapPoint(
    x: number, y: number, event: MouseEvent,
    frame: FrameLandmarks | null,
    rect: DOMRect,
    m: Measurement,
    pt: 'p1' | 'vertex' | 'p2',
  ): MeasurePoint {
    // Alt: H/V snap relative to connected point
    if (event.altKey) {
      const ref = pt === 'vertex'
        ? this.resolvedPt(m.p1, frame)
        : this.resolvedPt(m.vertex, frame);
      const dx = Math.abs(x - ref.x) * rect.width;
      const dy = Math.abs(y - ref.y) * rect.height;
      return { x: dx < dy ? ref.x : x, y: dx < dy ? y : ref.y, landmarkIdx: null };
    }
    // Shift: free
    if (event.shiftKey) return { x, y, landmarkIdx: null };
    // Default: snap to landmark
    if (frame) {
      const snap = this.nearestLandmark(x, y, rect.width, rect.height, frame);
      if (snap) return snap;
    }
    return { x, y, landmarkIdx: null };
  }

  private nearestLandmark(nx: number, ny: number, sw: number, sh: number, frame: FrameLandmarks): MeasurePoint | null {
    let best: MeasurePoint | null = null, bestDist = Infinity;
    for (let i = 0; i < frame.lm.length; i++) {
      const p = frame.lm[i];
      if (!p || (p[3] ?? 1) < 0.2) continue;
      const d = Math.hypot((p[0] - nx) * sw, (p[1] - ny) * sh);
      if (d < SNAP_PX && d < bestDist) { bestDist = d; best = { x: p[0], y: p[1], landmarkIdx: i }; }
    }
    return best;
  }

  private resolvedPt(mp: MeasurePoint, frame: FrameLandmarks | null): { x: number; y: number } {
    if (mp.landmarkIdx !== null && frame) {
      const p = frame.lm[mp.landmarkIdx];
      if (p) return { x: p[0], y: p[1] };
    }
    return { x: mp.x, y: mp.y };
  }

  // ── measurement drawing ───────────────────────────────────────────────────

  private drawMeasurements(ctx: CanvasRenderingContext2D, w: number, h: number, frame: FrameLandmarks): void {
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const cur = this.cursorNorm;

    for (let mIdx = 0; mIdx < this.measurements.length; mIdx++) {
      const m = this.measurements[mIdx];
      const p1 = this.resolvedPt(m.p1, frame);
      const vx = this.resolvedPt(m.vertex, frame);
      const p2 = this.resolvedPt(m.p2, frame);

      // Lines
      ctx.save();
      ctx.strokeStyle = '#111';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(p1.x * w, p1.y * h);
      ctx.lineTo(vx.x * w, vx.y * h);
      ctx.lineTo(p2.x * w, p2.y * h);
      ctx.stroke();
      ctx.restore();

      // Filled arc + label (angle computed in pixel space inside drawArcAt)
      this.drawArcAt(ctx, vx.x * w, vx.y * h, p1.x * w, p1.y * h, p2.x * w, p2.y * h);

      // Points — highlight the one being dragged
      for (const [ptKey, pos] of [['p1', p1], ['vertex', vx], ['p2', p2]] as [string, {x:number;y:number}][]) {
        const isDragging = this.activeDrag?.mIdx === mIdx && this.activeDrag?.pt === ptKey;
        const isHovered  = !this.activeDrag && cur
          && Math.hypot((pos.x - cur.x) * rect.width, (pos.y - cur.y) * rect.height) < SNAP_PX * 1.5;
        this.drawMeasurePoint(ctx, pos.x * w, pos.y * h, ptKey === 'vertex', isDragging || !!isHovered);
      }

      // Snap indicator while dragging near a landmark
      if (this.activeDrag?.mIdx === mIdx && cur && !this.activeDrag) {
        const snap = this.nearestLandmark(cur.x, cur.y, rect.width, rect.height, frame);
        if (snap) {
          ctx.save();
          ctx.strokeStyle = 'rgba(255,220,0,0.85)';
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.arc(snap.x * w, snap.y * h, 12, 0, Math.PI * 2);
          ctx.stroke();
          ctx.restore();
        }
      }
    }
  }

  private drawArcAt(ctx: CanvasRenderingContext2D, vx: number, vy: number, p1x: number, p1y: number, p2x: number, p2y: number): void {
    const dx1 = p1x - vx, dy1 = p1y - vy;
    const dx2 = p2x - vx, dy2 = p2y - vy;
    const arm1 = Math.hypot(dx1, dy1);
    const arm2 = Math.hypot(dx2, dy2);
    if (arm1 < 1 || arm2 < 1) return;

    const a1 = Math.atan2(dy1, dx1);
    const a2 = Math.atan2(dy2, dx2);
    const arcR = Math.min(32, Math.min(arm1, arm2) * 0.38);
    if (arcR < 4) return;

    // Angle computed in pixel space — correct for any video aspect ratio
    const dot = dx1 * dx2 + dy1 * dy2;
    const angle = Math.acos(Math.max(-1, Math.min(1, dot / (arm1 * arm2)))) * 180 / Math.PI;

    const cwSpan = ((a2 - a1) + 2 * Math.PI) % (2 * Math.PI);
    const anticlockwise = cwSpan > Math.PI;
    const midA = anticlockwise ? a1 - (2 * Math.PI - cwSpan) / 2 : a1 + cwSpan / 2;

    ctx.save();
    ctx.beginPath();
    ctx.moveTo(vx, vy);
    ctx.arc(vx, vy, arcR, a1, a2, anticlockwise);
    ctx.closePath();
    ctx.fillStyle = 'rgba(100,150,255,0.32)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(80,130,255,0.75)';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    const lx = vx + Math.cos(midA) * (arcR + 15);
    const ly = vy + Math.sin(midA) * (arcR + 15);
    ctx.font = 'bold 13px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.strokeStyle = 'rgba(255,255,255,0.9)';
    ctx.lineWidth = 3;
    ctx.strokeText(`${Math.round(angle)}°`, lx, ly);
    ctx.fillStyle = '#111';
    ctx.fillText(`${Math.round(angle)}°`, lx, ly);
    ctx.restore();
  }

  private drawMeasurePoint(ctx: CanvasRenderingContext2D, px: number, py: number, isVertex: boolean, active: boolean): void {
    ctx.save();
    const r = active ? 8 : (isVertex ? 5 : 6);
    ctx.beginPath();
    ctx.arc(px, py, r, 0, Math.PI * 2);
    ctx.fillStyle = active
      ? 'rgba(100,180,255,0.95)'
      : isVertex ? 'rgba(100,150,255,0.9)' : 'rgba(220,220,220,0.92)';
    ctx.fill();
    ctx.strokeStyle = active ? '#0066cc' : '#333';
    ctx.lineWidth = active ? 2 : 1.5;
    ctx.stroke();
    ctx.restore();
  }
}
