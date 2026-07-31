import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { TabsModule } from 'primeng/tabs';

import { MonitoringService } from '../../core/services/monitoring.service';
import { SchemaAuditRecord, TableSchemaSummary } from '../../core/models/schema.model';

@Component({
  selector: 'app-schema',
  imports: [TranslatePipe, TableModule, TagModule, ButtonModule, TabsModule, DatePipe],
  templateUrl: './schema.html',
})
export class Schema {
  private monitoring = inject(MonitoringService);

  readonly summary = signal<TableSchemaSummary[]>([]);
  readonly audit = signal<SchemaAuditRecord[]>([]);
  readonly loading = signal(true);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.monitoring.getSchemaSummary().subscribe({
      next: (s) => { this.summary.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.monitoring.getSchemaAudit(100).subscribe({
      next: (a) => this.audit.set(a),
    });
  }
}