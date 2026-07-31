import { Component, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';

import { MonitoringService } from '../../core/services/monitoring.service';
import { Reconciliation as ReconciliationModel } from '../../core/models/monitoring.model';

@Component({
  selector: 'app-reconciliation',
  imports: [TranslatePipe, TableModule, TagModule, ButtonModule, DecimalPipe],
  templateUrl: './reconciliation.html',
})
export class Reconciliation {
  private monitoring = inject(MonitoringService);

  readonly rows = signal<ReconciliationModel[]>([]);
  readonly loading = signal(true);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.monitoring.getReconciliation().subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}