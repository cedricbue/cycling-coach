import { Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { FtpEntry } from '../../../../core/api/model/models';

@Component({
  selector: 'app-ftp-history',
  imports: [DatePipe, MatIconModule],
  templateUrl: './ftp-history.component.html',
  styleUrl: './ftp-history.component.scss',
})
export class FtpHistoryComponent {
  readonly history = input<FtpEntry[]>([]);
}
