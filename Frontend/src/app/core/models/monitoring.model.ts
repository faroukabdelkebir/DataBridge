// ─────────────────────────────────────────────────────────────
// MonitoringMS — miroir des DTOs Java
// Les LocalDateTime Java arrivent en ISO-8601 sans timezone
// (ex: '2026-07-06T14:32:10'), les LocalTime en 'HH:mm:ss'.
// ─────────────────────────────────────────────────────────────

/** Statuts Spring Batch. */
export type BatchStatus =
  | 'STARTING' | 'STARTED' | 'STOPPING' | 'STOPPED'
  | 'COMPLETED' | 'ABANDONED' | 'FAILED' | 'UNKNOWN';

// ── Réconciliation ───────────────────────────────────────────

export interface Reconciliation {
  tableName: string;
  businessDate: number;        // format yyyyMMdd (ex: 20260706)
  oracleCount: number;
  sqlServerCount: number;
  delta: number;               // oracleCount - sqlServerCount
  reconciliationRate: number;  // %
  reconciled: boolean;
  status: string;
}

// ── Exécutions de jobs ───────────────────────────────────────

export interface JobExecution {
  jobExecutionId: number;
  jobInstanceId: number;
  jobName: string;
  status: BatchStatus | string;
  exitCode: string | null;
  exitMessage: string | null;
  startTime: string | null;    // LocalDateTime ISO
  endTime: string | null;
  createTime: string | null;
  durationSeconds: number | null;
  running: boolean;
  failed: boolean;
  totalReadCount: number;
  totalWriteCount: number;
  totalSkipCount: number;
  throughputPerSecond: number | null;
  reconciliation: Reconciliation | null;
}

export interface StepExecution {
  stepExecutionId: number;
  jobExecutionId: number;
  stepName: string;
  status: BatchStatus | string;
  exitCode: string | null;
  startTime: string | null;
  endTime: string | null;
  durationSeconds: number | null;
  readCount: number;
  writeCount: number;
  filterCount: number;
  skipCount: number;
  rollbackCount: number;
  commitCount: number;
  throughputPerSecond: number | null;
}

// ── Alertes ──────────────────────────────────────────────────

export interface Alert {
  alertKey?: string;
  type: string;                            // fallback brut (déprécié)
  typeKey?: string | null;                 // clé i18n du type
  severity: string;                        // code : 'CRITICAL' | 'WARNING' | 'INFO'
  jobName: string | null;
  message: string;                         // fallback brut (déprécié)
  messageKey?: string | null;              // clé i18n du message
  params?: Record<string, unknown> | null; // paramètres d'interpolation
  triggeredAt: string;                     // LocalDateTime ISO
  resolved: boolean;
}

// ── SLA ──────────────────────────────────────────────────────

export interface SlaStatus {
  jobName: string;
  slaDeadline: string;         // LocalTime 'HH:mm:ss'
  lastCompletionTime: string | null;
  slaBreached: boolean;
  lateBySeconds: number;       // 0 si dans les délais
  status: string;
}

// ── KPIs ─────────────────────────────────────────────────────

export interface DailyVolume {
  date: string;                // 'yyyy-MM-dd'
  rowsRead: number;
  rowsWritten: number;
  jobCount: number;
  successRate: number;
}

export interface Kpi {
  windowDays: number;

  overallSuccessRate: number;
  successRateByJob: Record<string, number>;

  totalRowsExtracted: number;
  rowsByTable: Record<string, number>;

  avgThroughputPerSec: number;
  throughputByJob: Record<string, number>;

  avgDurationSeconds: number;
  avgDurationByJob: Record<string, number>;

  lastSuccessfulBusinessDate: string | null;
  consecutiveSuccessCount: number;

  failureCount: number;
  consecutiveFailureCount: number;
  lastFailureMessage: string | null;

  volumeTrend: DailyVolume[];
  computedAt: string;          // LocalDateTime ISO
}

// ── Dashboard ────────────────────────────────────────────────

export interface DashboardSummary {
  runningJobsCount: number;
  completedTodayCount: number;
  failedTodayCount: number;
  successRate7d: number;
  cobStatus: string;
  lastBusinessDate: string | null;
  liveJobs: JobExecution[];
  recentExecutions: JobExecution[];
  activeAlerts: Alert[];
  generatedAt: string;
}