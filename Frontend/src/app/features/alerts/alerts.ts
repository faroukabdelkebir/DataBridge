import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';

import { MonitoringService } from '../../core/services/monitoring.service';
import { Alert } from '../../core/models/monitoring.model';

@Component({
  selector: 'app-alerts',
  imports: [TranslatePipe, TableModule, TagModule, ButtonModule, DatePipe],
  templateUrl: './alerts.html',
})
export class Alerts {
  private monitoring = inject(MonitoringService);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);

  readonly alerts = signal<Alert[]>([]);
  readonly loading = signal(true);
  readonly resolving = signal<string | null>(null);

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.monitoring.getAlerts().subscribe({
      next: (a) => { this.alerts.set(a); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  resolve(alert: Alert): void {
    if (!alert.alertKey) return;
    this.resolving.set(alert.alertKey);
    this.monitoring.resolveAlert(alert.alertKey).subscribe({
      next: () => {
        this.alerts.update((list) => list.filter((a) => a.alertKey !== alert.alertKey));
        this.resolving.set(null);
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('alerts.resolved'),
          detail: alert.message,
        });
      },
      error: () => {
        this.resolving.set(null);
        this.toast.add({ severity: 'error', summary: this.translate.instant('common.error') });
      },
    });
  }

  alertSeverity(severity: string): 'danger' | 'warn' | 'info' {
    switch (severity?.toUpperCase()) {
      case 'CRITICAL': case 'HIGH': return 'danger';
      case 'WARNING': case 'MEDIUM': return 'warn';
      default: return 'info';
    }
  }
}