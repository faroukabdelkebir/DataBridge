import { Alert, DailyVolume, Kpi, Reconciliation, SlaStatus } from './monitoring.model';

/** Alerte condensée dans les rapports (ReportService.AlertSummary). */
export interface AlertSummary {
  type: string;
  severity: string;
  jobName: string | null;
  message: string;
}

/** GET /reports/daily */
export interface DailyReport {
  reportDate: string;          // LocalDate 'yyyy-MM-dd'
  generatedAt: string;
  kpis1Day: Kpi;
  kpis7Days: Kpi;
  reconciliations: Reconciliation[];
  slaStatuses: SlaStatus[];
  activeAlerts: AlertSummary[];
  overallStatus: string;
}

/** GET /reports/weekly */
export interface WeeklyReport {
  weekStart: string;
  weekEnd: string;
  generatedAt: string;
  kpis: Kpi;
  successRateDeltaVsPreviousWeek: number;
  topVolumeTable: string | null;
  trend: DailyVolume[];
}