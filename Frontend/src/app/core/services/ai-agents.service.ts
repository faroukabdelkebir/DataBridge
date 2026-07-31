import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API } from '../config/api.config';
import {
  AgentReportRow, AiHealth, AuditAllReport, AuditResponse,
  QualityReport, ReportList, SchedulerReco,
} from '../models/ai-agents.model';

@Injectable({ providedIn: 'root' })
export class AiAgentsService {
  private http = inject(HttpClient);

  getHealth(): Observable<AiHealth> {
    return this.http.get<AiHealth>(API.aiAgents.health);
  }

  // ── M1 ─────────────────────────────────────────────────────
  quickScore(tableName: string, model: string): Observable<QualityReport> {
    return this.http.post<QualityReport>(API.aiAgents.quickScore, {
      table_name: tableName, model,
    });
  }

  audit(tableName: string, model: string): Observable<AuditResponse> {
    return this.http.post<AuditResponse>(API.aiAgents.audit, {
      table_name: tableName, model,
    });
  }

  auditAll(model: string): Observable<{ status: string; report_id: number; report: AuditAllReport }> {
    return this.http.post<{ status: string; report_id: number; report: AuditAllReport }>(
      API.aiAgents.auditAll, { model },
    );
  }

  // ── M3 (asynchrone) ────────────────────────────────────────
  getRecommendation(): Observable<SchedulerReco> {
    return this.http.get<SchedulerReco>(API.aiAgents.schedulerRecommend);
  }

  launchRecommendation(model: string, refresh = false): Observable<SchedulerReco> {
    return this.http.post<SchedulerReco>(API.aiAgents.schedulerRecommend, { model, refresh });
  }

  shouldLaunch(batchId: string): Observable<unknown> {
    return this.http.post<unknown>(API.aiAgents.shouldLaunch, { batch_id: batchId });
  }

  getSchedulerConfig(): Observable<unknown> {
    return this.http.get<unknown>(API.aiAgents.schedulerConfig);
  }

  // ── Historique ─────────────────────────────────────────────
  listReports(): Observable<ReportList> {
    return this.http.get<ReportList>(API.aiAgents.reports);
  }

  getReport(id: number): Observable<AgentReportRow> {
    return this.http.get<AgentReportRow>(API.aiAgents.reportById(id));
  }
}