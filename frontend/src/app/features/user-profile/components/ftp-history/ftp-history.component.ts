import { Component, input } from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { FtpEntry } from '../../../../core/api/model/models';

@Component({
  selector: 'app-ftp-history',
  imports: [DatePipe, DecimalPipe, RouterLink, MatIconModule],
  templateUrl: './ftp-history.component.html',
  styleUrl: './ftp-history.component.scss',
})
export class FtpHistoryComponent {
  readonly history = input<FtpEntry[]>([]);
}
