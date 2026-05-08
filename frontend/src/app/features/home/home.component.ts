import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { SyncButtonComponent } from '../activities/sync-button/sync-button.component';

@Component({
  selector: 'app-home',
  imports: [RouterOutlet, MatToolbarModule, SyncButtonComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent {}
