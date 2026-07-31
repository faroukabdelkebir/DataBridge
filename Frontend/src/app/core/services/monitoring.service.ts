import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API } from '../config/api.config';
import {
  Alert,
  DashboardSummary,
  JobExecution,
  Kpi,
  Reconciliation,
  SlaStatus,
  StepExecution,
} from '../models/monitoring.model';
import {
  SchemaAuditRecord,
  SchemaSourceRecord,
  TableSchemaSummary,
} from '../models/schema.model';
import { BatchConfigRecord } from '../models/batch-config.model';
import { DailyReport, WeeklyReport } from '../models/report.model';

@Injectable({ providedIn: 'root' })
export class MonitoringService {
  private http = inject(HttpClient);

  // ── Dashboard ──────────────────────────────────────────────
  getSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(API.monitoring.dashboardSummary);
  }

  getLiveJobs(): Observable<JobExecution[]> {
    return this.http.get<JobExecution[]>(API.monitoring.liveJobs);
  }

  // ── Jobs ───────────────────────────────────────────────────
  getRecentJobs(limit = 20): Observable<JobExecution[]> {
    return this.http.get<JobExecution[]>(API.monitoring.jobs, {
      params: new HttpParams().set('limit', limit),
    });
  }

  getJobsHistory(days = 7): Observable<JobExecution[]> {
    return this.http.get<JobExecution[]>(API.monitoring.jobsHistory, {
      params: new HttpParams().set('days', days),
    });
  }

  getJobById(id: number): Observable<JobExecution> {
    return this.http.get<JobExecution>(API.monitoring.jobById(id));
  }

  getJobSteps(id: number): Observable<StepExecution[]> {
    return this.http.get<StepExecution[]>(API.monitoring.jobSteps(id));
  }

  getJobsByName(jobName: string): Observable<JobExecution[]> {
    return this.http.get<JobExecution[]>(API.monitoring.jobsByName(jobName));
  }

  // ── KPIs / Réconciliation / SLA ────────────────────────────
  getKpis(days = 7): Observable<Kpi> {
    return this.http.get<Kpi>(API.monitoring.kpis, {
      params: new HttpParams().set('days', days),
    });
  }

  getReconciliation(): Observable<Reconciliation[]> {
    return this.http.get<Reconciliation[]>(API.monitoring.reconciliation);
  }

  getReconciliationByTable(table: string): Observable<Reconciliation[]> {
    return this.http.get<Reconciliation[]>(API.monitoring.reconciliationByTable(table));
  }

  getSla(): Observable<SlaStatus[]> {
    return this.http.get<SlaStatus[]>(API.monitoring.sla);
  }

  getSlaByJob(jobName: string): Observable<SlaStatus> {
    return this.http.get<SlaStatus>(API.monitoring.slaByJob(jobName));
  }

  // ── Alertes ────────────────────────────────────────────────
  getAlerts(): Observable<Alert[]> {
    return this.http.get<Alert[]>(API.monitoring.alerts);
  }

  resolveAlert(alertKey: string): Observable<Record<string, unknown>> {
    return this.http.delete<Record<string, unknown>>(API.monitoring.resolveAlert(alertKey));
  }

  // ── Schéma ─────────────────────────────────────────────────
  getSchemaSummary(): Observable<TableSchemaSummary[]> {
    return this.http.get<TableSchemaSummary[]>(API.monitoring.schemaSummary);
  }

  getSchemaColumns(): Observable<SchemaSourceRecord[]> {
    return this.http.get<SchemaSourceRecord[]>(API.monitoring.schemaColumns);
  }

  getSchemaColumnsByTable(table: string): Observable<SchemaSourceRecord[]> {
    return this.http.get<SchemaSourceRecord[]>(API.monitoring.schemaColumnsByTable(table));
  }

  getSchemaAudit(limit = 50): Observable<SchemaAuditRecord[]> {
    return this.http.get<SchemaAuditRecord[]>(API.monitoring.schemaAudit, {
      params: new HttpParams().set('limit', limit),
    });
  }

  getSchemaAuditByTable(table: string): Observable<SchemaAuditRecord[]> {
    return this.http.get<SchemaAuditRecord[]>(API.monitoring.schemaAuditByTable(table));
  }

  // ── Rapports ───────────────────────────────────────────────
  getDailyReport(): Observable<DailyReport> {
    return this.http.get<DailyReport>(API.monitoring.reportDaily);
  }

  getWeeklyReport(): Observable<WeeklyReport> {
    return this.http.get<WeeklyReport>(API.monitoring.reportWeekly);
  }

  // ── Configuration batch ────────────────────────────────────
  getBatchConfigs(): Observable<BatchConfigRecord[]> {
    return this.http.get<BatchConfigRecord[]>(API.monitoring.configBatch);
  }

  getBatchConfigById(id: string): Observable<BatchConfigRecord> {
    return this.http.get<BatchConfigRecord>(API.monitoring.configBatchById(id));
  }
}