import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Observable } from 'rxjs';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MessageModule } from 'primeng/message';
import { ConfirmationService, MessageService } from 'primeng/api';

import { ExtractorService } from '../../core/services/extractor.service';
import { MonitoringService } from '../../core/services/monitoring.service';
import { JobStreamService } from '../../core/services/job-stream.service';
import { SchemaSourceRecord } from '../../core/models/schema.model';
import { BatchConfigRecord } from '../../core/models/batch-config.model';

interface JobCard {
  key: string;
  jobName: string;        // nom Spring Batch réel (flux SSE)
  labelKey: string;
  descKey: string;
  icon: string;
  start: () => Observable<void>;
}

@Component({
  selector: 'app-batch-control',
  imports: [
    TranslatePipe, FormsModule, ButtonModule, TagModule, TableModule,
    SelectModule, ToggleSwitchModule, ConfirmDialogModule, MessageModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './batch-control.html',
})
export class BatchControl {
  private extractor = inject(ExtractorService);
  private monitoring = inject(MonitoringService);
  private stream = inject(JobStreamService);
  private confirm = inject(ConfirmationService);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);

  // ── Pilotage des jobs ────────────────────────────────────────
  readonly jobs: JobCard[] = [
    { key: 'main',     jobName: 'T24',                 labelKey: 'batch.main',     descKey: 'batch.mainDesc',     icon: 'pi pi-database',   start: () => this.extractor.startMain() },
    { key: 'customer', jobName: 'CUSTOMER.JOB',        labelKey: 'batch.customer', descKey: 'batch.customerDesc', icon: 'pi pi-users',      start: () => this.extractor.startCustomer() },
    { key: 'ftHis',    jobName: 'FT.HIS.JOB',          labelKey: 'batch.ftHis',    descKey: 'batch.ftHisDesc',    icon: 'pi pi-arrow-right-arrow-left', start: () => this.extractor.startFtHis() },
    { key: 'bills',    jobName: 'AA.BILL.DETAILS.JOB', labelKey: 'batch.bills',    descKey: 'batch.billsDesc',    icon: 'pi pi-file',       start: () => this.extractor.startBillDetails() },
  ];

  readonly launching = signal<string | null>(null);
  readonly stopping = signal(false);
  readonly streamConnected = this.stream.connected;

  /** Noms des jobs actuellement en exécution (flux SSE). */
  readonly runningNames = computed<Set<string>>(
    () => new Set(this.stream.liveJobs().filter((j) => j.running).map((j) => j.jobName)),
  );
  readonly mainRunning = computed(() => this.runningNames().has('T24'));

  constructor() {
    this.stream.connect();
    this.destroyRef.onDestroy(() => this.stream.disconnect());

    this.monitoring.getSchemaSummary().subscribe({
      next: (s) => {
        this.tables.set(s.map((t) => t.tableName));
        if (s.length) { this.selectedTable = s[0].tableName; this.loadColumns(); }
      },
    });

    this.loadConfigs();
  }

  isRunning(job: JobCard): boolean {
    return this.runningNames().has(job.jobName);
  }

  askStart(job: JobCard): void {
    this.confirm.confirm({
      header: this.translate.instant('common.confirm'),
      message: this.translate.instant('batch.confirmStart', {
        name: this.translate.instant(job.labelKey),
      }),
      icon: 'pi pi-play-circle',
      accept: () => this.doStart(job),
    });
  }

  private doStart(job: JobCard): void {
    this.launching.set(job.key);
    job.start().subscribe({
      next: () => {
        this.launching.set(null);
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('batch.started'),
          detail: job.jobName,
        });
      },
      error: (err) => {
        this.launching.set(null);
        this.toast.add({
          severity: 'error',
          summary: this.translate.instant('batch.startFailed'),
          detail: err?.error?.message ?? job.jobName,
          life: 6000,
        });
      },
    });
  }

  askStop(): void {
    this.confirm.confirm({
      header: this.translate.instant('common.confirm'),
      message: this.translate.instant('batch.confirmStop'),
      icon: 'pi pi-stop-circle',
      acceptButtonProps: { severity: 'danger' },
      accept: () => {
        this.stopping.set(true);
        this.extractor.stopMain().subscribe({
          next: () => {
            this.stopping.set(false);
            this.toast.add({ severity: 'warn', summary: this.translate.instant('batch.stopRequested') });
          },
          error: () => this.stopping.set(false),
        });
      },
    });
  }

  // ── Planification automatique (BATCH_CONFIG, extractor) ─────
  readonly configs = signal<BatchConfigRecord[]>([]);
  readonly configsLoading = signal(false);
  readonly pendingConfig = signal<string | null>(null);

  loadConfigs(): void {
    this.configsLoading.set(true);
    this.extractor.getBatchConfigs().subscribe({
      next: (c) => { this.configs.set(c); this.configsLoading.set(false); },
      error: () => this.configsLoading.set(false),
    });
  }

  /** Bascule extraction active/inactive (colonne enabled). */
  toggleConfigEnabled(cfg: BatchConfigRecord, enabled: boolean): void {
    this.patchConfig(cfg, { ...cfg, enabled }, () =>
      this.extractor.toggleBatchEnabled(cfg.id, enabled));
  }

  /** Bascule la planification automatique on/off. */
  toggleConfigScheduler(cfg: BatchConfigRecord, scheduler: boolean): void {
    if (scheduler && !cfg.schedulerTime) {
      // pas d'heure définie : proposer une valeur par défaut avant activation
      this.patchConfig(cfg, { ...cfg, scheduler, schedulerTime: '06:00' }, () =>
        this.extractor.updateBatchScheduler(cfg.id, scheduler, '06:00'));
      return;
    }
    this.patchConfig(cfg, { ...cfg, scheduler }, () =>
      this.extractor.updateBatchScheduler(cfg.id, scheduler, null));
  }

  /** Change l'heure de déclenchement (input type=time → HH:mm). */
  changeSchedulerTime(cfg: BatchConfigRecord, time: string): void {
    if (!time || time === cfg.schedulerTime) return;
    this.patchConfig(cfg, { ...cfg, schedulerTime: time }, () =>
      this.extractor.updateBatchScheduler(cfg.id, cfg.scheduler, time));
  }

  /** Mise à jour optimiste + rollback en cas d'erreur (même patron que toggleColumn). */
  private patchConfig(
    before: BatchConfigRecord,
    optimistic: BatchConfigRecord,
    call: () => Observable<Record<string, unknown>>,
  ): void {
    this.pendingConfig.set(before.id);
    this.configs.update((list) =>
      list.map((c) => (c.id === before.id ? optimistic : c)));

    call().subscribe({
      next: () => {
        this.pendingConfig.set(null);
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('batch.scheduler.saved'),
          detail: before.id,
          life: 2500,
        });
      },
      error: (err) => {
        this.pendingConfig.set(null);
        this.configs.update((list) =>
          list.map((c) => (c.id === before.id ? before : c)));
        this.toast.add({
          severity: 'error',
          summary: this.translate.instant('common.error'),
          detail: err?.error?.error ?? before.id,
          life: 5000,
        });
      },
    });
  }

  // ── Colonnes sources ─────────────────────────────────────────
  readonly tables = signal<string[]>([]);
  selectedTable = '';
  readonly columns = signal<SchemaSourceRecord[]>([]);
  readonly columnsLoading = signal(false);
  readonly pendingColumn = signal<string | null>(null);

  loadColumns(): void {
    if (!this.selectedTable) return;
    this.columnsLoading.set(true);
    this.extractor.getColumns(this.selectedTable).subscribe({
      next: (cols) => { this.columns.set(cols); this.columnsLoading.set(false); },
      error: () => this.columnsLoading.set(false),
    });
  }

  toggleColumn(col: SchemaSourceRecord, enabled: boolean): void {
    this.pendingColumn.set(col.id);
    // Optimiste : on bascule tout de suite, on revient en arrière si erreur
    this.columns.update((list) =>
      list.map((c) => (c.id === col.id ? { ...c, enabled } : c)));

    this.extractor.setColumnEnabled(col.tableName, col.columnName, enabled).subscribe({
      next: (updated) => {
        this.pendingColumn.set(null);
        this.columns.update((list) =>
          list.map((c) => (c.id === updated.id ? updated : c)));
      },
      error: () => {
        this.pendingColumn.set(null);
        this.columns.update((list) =>
          list.map((c) => (c.id === col.id ? { ...c, enabled: !enabled } : c)));
        this.toast.add({ severity: 'error', summary: this.translate.instant('common.error') });
      },
    });
  }
}