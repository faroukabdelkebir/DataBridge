import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';

import { MonitoringService } from '../../core/services/monitoring.service';
import { SlaStatus } from '../../core/models/monitoring.model';
import { DurationPipe } from '../../shared/pipes/duration.pipe';

@Component({
  selector: 'app-sla',
  imports: [TranslatePipe, TableModule, TagModule, ButtonModule, DatePipe, DurationPipe],
  templateUrl: './sla.html',
})
export class Sla {
  private monitoring = inject(MonitoringService);

  readonly slas = signal<SlaStatus[]>([]);
  readonly loading = signal(true);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.monitoring.getSla().subscribe({
      next: (s) => { this.slas.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}