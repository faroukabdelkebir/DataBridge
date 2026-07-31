import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DatePipe, DecimalPipe, JsonPipe } from '@angular/common';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TabsModule } from 'primeng/tabs';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TableModule } from 'primeng/table';
import { DialogModule } from 'primeng/dialog';
import { ProgressBarModule } from 'primeng/progressbar';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';

import { AiAgentsService } from '../../core/services/ai-agents.service';
import { MonitoringService } from '../../core/services/monitoring.service';
import { QualityPdfService } from '../../core/services/quality-pdf.service';
import { AuthService } from '../../core/services/auth.service';
import { SmartReport } from '../../shared/components/smart-report/smart-report';
import {
  AgentReportRow, AiHealth, QualityReport, SchedulerReco,
} from '../../core/models/ai-agents.model';

const DIMENSION_LABELS: Record<string, string> = {
  D1: 'Complétude', D2: 'Unicité', D3: 'Couverture schéma',
  D4: 'Conformité', D5: 'Fraîcheur', D6: 'Consistance',
};

@Component({
  selector: 'app-assistant',
  imports: [
    TranslatePipe, FormsModule, DatePipe, DecimalPipe, JsonPipe,
    TabsModule, ButtonModule, SelectModule, TagModule, TableModule,
    DialogModule, ProgressBarModule, MessageModule, SmartReport,
  ],
  templateUrl: './assistant.html',
})
export class Assistant {
  private ai = inject(AiAgentsService);
  private monitoring = inject(MonitoringService);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);
  private destroyRef = inject(DestroyRef);
  private pdf = inject(QualityPdfService);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(AuthService);

  /** Visiteur : accès limité à l'historique des rapports. */
  readonly isVisitor = computed(() => this.auth.hasRole('VISITOR'));

  // ── Santé du service + modèles disponibles ─────────────────
  readonly health = signal<AiHealth | null>(null);
  readonly healthError = signal(false);
  readonly ollamaDown = computed(() => this.health() !== null && !this.health()!.ollama.available);

  // ── Sélections ─────────────────────────────────────────────
  readonly tables = signal<string[]>([]);
  selectedTable = '';
  selectedModel = '';

  // ── M1 : qualité ───────────────────────────────────────────
  readonly scoring = signal(false);
  readonly auditing = signal(false);
  readonly elapsed = signal(0);
  private elapsedTimer: ReturnType<typeof setInterval> | null = null;
  readonly report = signal<QualityReport | null>(null);

  // ── Viewer PDF du rapport qualité ──────────────────────────
  readonly pdfVisible = signal(false);
  readonly pdfUrl = signal<SafeResourceUrl | null>(null);
  private pdfBlobUrl: string | null = null;
  private pdfFileName = 'rapport.pdf';

  // ── M3 : ordonnancement ────────────────────────────────────
  readonly reco = signal<SchedulerReco | null>(null);
  readonly recoLaunching = signal(false);
  readonly showRawPlan = signal(false);
  private pollTimer: ReturnType<typeof setInterval> | null = null;

  // ── Historique ─────────────────────────────────────────────
  readonly reports = signal<AgentReportRow[]>([]);
  readonly reportsLoading = signal(false);
  readonly detail = signal<AgentReportRow | null>(null);
  readonly detailVisible = signal(false);
  readonly showRawDetail = signal(false);

  constructor() {
    // Visiteur : uniquement l'historique des rapports, pas les agents IA.
    if (this.isVisitor()) {
      this.loadReports();
      this.destroyRef.onDestroy(() => {
        this.stopPolling();
        this.stopElapsed();
        this.revokePdf();
      });
      return;
    }

    this.ai.getHealth().subscribe({
      next: (h) => {
        this.health.set(h);
        this.selectedModel = h.ollama.active_model || h.ollama.models[0] || '';
      },
      error: () => this.healthError.set(true),
    });

    // Tables auditables : celles du schéma des sources (MonitoringMS)
    this.monitoring.getSchemaSummary().subscribe({
      next: (s) => {
        this.tables.set(s.map((t) => t.tableName));
        this.selectedTable = s[0]?.tableName ?? '';
      },
    });

    this.refreshReco();
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.stopElapsed();
      this.revokePdf();
    });
  }

  // ═══ M1 — Qualité ═══════════════════════════════════════════

  quickScore(): void {
    if (!this.selectedTable) return;
    this.scoring.set(true);
    this.report.set(null);
    this.startElapsed();
    this.ai.quickScore(this.selectedTable, this.selectedModel).subscribe({
      next: (r) => { this.report.set(r); this.scoring.set(false); this.stopElapsed(); },
      error: (err) => {
        this.scoring.set(false); this.stopElapsed();
        this.agentError(err);
      },
    });
  }

  fullAudit(): void {
    if (!this.selectedTable) return;
    this.auditing.set(true);
    this.report.set(null);
    this.startElapsed();
    this.ai.audit(this.selectedTable, this.selectedModel).subscribe({
      next: (res) => {
        this.report.set(res.report);
        this.auditing.set(false); this.stopElapsed();
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('assistant.auditDone'),
          detail: `Rapport #${res.report_id}`,
        });
        this.loadReports();
      },
      error: (err) => {
        this.auditing.set(false); this.stopElapsed();
        this.agentError(err);
      },
    });
  }

  readonly dimensionRows = computed(() => {
    const dims = this.report()?.dimensions ?? {};
    return Object.entries(dims).map(([key, v]) => ({
      key,
      label: DIMENSION_LABELS[key] ?? key,
      score: v.score,
    }));
  });

  gradeSeverity(grade?: string): 'success' | 'info' | 'warn' | 'danger' {
    switch (grade) {
      case 'A': return 'success';
      case 'B': return 'info';
      case 'C': return 'warn';
      default: return 'danger';
    }
  }

  scoreColor(score: number): string {
    return score >= 80 ? '#22c55e' : score >= 50 ? '#f59e0b' : '#ef4444';
  }

  // ── Export / viewer PDF ────────────────────────────────────

  /** Génère le PDF du rapport courant et l'affiche dans le viewer intégré. */
  openPdf(): void {
    const r = this.report();
    if (!r) return;
    this.revokePdf();
    const blob = this.pdf.build(r);
    this.pdfFileName = this.pdf.fileName(r);
    this.pdfBlobUrl = URL.createObjectURL(blob);
    // #toolbar=1 : garde la barre d'outils native du viewer PDF du navigateur
    this.pdfUrl.set(
      this.sanitizer.bypassSecurityTrustResourceUrl(this.pdfBlobUrl + '#toolbar=1'),
    );
    this.pdfVisible.set(true);
  }

  /** Force le téléchargement du PDF généré. */
  downloadPdf(): void {
    if (!this.pdfBlobUrl) return;
    const a = document.createElement('a');
    a.href = this.pdfBlobUrl;
    a.download = this.pdfFileName;
    a.click();
  }

  closePdf(): void {
    this.pdfVisible.set(false);
    this.revokePdf();
  }

  private revokePdf(): void {
    if (this.pdfBlobUrl) { URL.revokeObjectURL(this.pdfBlobUrl); this.pdfBlobUrl = null; }
    this.pdfUrl.set(null);
  }

  // ═══ M3 — Ordonnancement ════════════════════════════════════

  refreshReco(): void {
    this.ai.getRecommendation().subscribe({
      next: (r) => {
        this.reco.set(r);
        if (r.status === 'computing') this.startPolling();
        else this.stopPolling();
      },
    });
  }

  launchReco(refresh = true): void {
    this.recoLaunching.set(true);
    this.ai.launchRecommendation(this.selectedModel, refresh).subscribe({
      next: (r) => {
        this.recoLaunching.set(false);
        this.reco.set(r);
        if (r.status === 'computing') this.startPolling();
      },
      error: (err) => { this.recoLaunching.set(false); this.agentError(err); },
    });
  }

  private startPolling(): void {
    if (this.pollTimer) return;
    this.pollTimer = setInterval(() => this.refreshReco(), 5_000);
  }

  private stopPolling(): void {
    if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; }
  }

  // ═══ Historique ═════════════════════════════════════════════

  loadReports(): void {
    this.reportsLoading.set(true);
    this.ai.listReports().subscribe({
      next: (res) => { this.reports.set(res.results); this.reportsLoading.set(false); },
      error: () => this.reportsLoading.set(false),
    });
  }

  openDetail(row: AgentReportRow): void {
    this.detailVisible.set(true);
    this.showRawDetail.set(false);
    this.detail.set(null);
    this.ai.getReport(row.id).subscribe({
      next: (r) => this.detail.set(r),
      error: () => this.detail.set(row),
    });
  }

  // ═══ Helpers ════════════════════════════════════════════════

  private startElapsed(): void {
    this.elapsed.set(0);
    this.elapsedTimer = setInterval(() => this.elapsed.update((v) => v + 1), 1_000);
  }

  private stopElapsed(): void {
    if (this.elapsedTimer) { clearInterval(this.elapsedTimer); this.elapsedTimer = null; }
  }

  private agentError(err: { status?: number; error?: { error?: string } }): void {
    // 503 = Ollama indisponible côté agent ; le reste est géré par errorInterceptor
    if (err?.status === 503) {
      this.toast.add({
        severity: 'error',
        summary: this.translate.instant('assistant.agentUnavailable'),
        detail: err.error?.error ?? '',
        life: 6000,
      });
    }
  }
}