import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { API } from '../config/api.config';
import { SchemaSourceRecord } from '../models/schema.model';
import { BatchConfigRecord } from '../models/batch-config.model';

@Injectable({ providedIn: 'root' })
export class ExtractorService {
  private http = inject(HttpClient);

  // ── Pilotage des jobs (réponse vide, 500 si déjà en cours) ──
  startMain(): Observable<void>        { return this.http.post<void>(API.extractor.startMain, {}); }
  startCustomer(): Observable<void>    { return this.http.post<void>(API.extractor.startCustomer, {}); }
  startFtHis(): Observable<void>       { return this.http.post<void>(API.extractor.startFtHis, {}); }
  startBillDetails(): Observable<void> { return this.http.post<void>(API.extractor.startBills, {}); }

  /** N'arrête QUE l'exécution T24 en cours (comportement backend). */
  stopMain(): Observable<void>         { return this.http.post<void>(API.extractor.stopMain, {}); }

  // ── Schema source ────────────────────────────────────────────
  getColumns(tableName: string): Observable<SchemaSourceRecord[]> {
    return this.http.get<SchemaSourceRecord[]>(API.extractor.schemaColumns(tableName));
  }

  setColumnEnabled(tableName: string, columnName: string, enabled: boolean): Observable<SchemaSourceRecord> {
    const params = new HttpParams().set('tableName', tableName).set('columnName', columnName);
    return this.http.patch<SchemaSourceRecord>(API.extractor.toggleColumn, { enabled }, { params });
  }
  // ── Planification automatique (BATCH_CONFIG, lue par le scheduler) ──
  getBatchConfigs(): Observable<BatchConfigRecord[]> {
    return this.http.get<BatchConfigRecord[]>(API.extractor.schedulerConfigs);
  }

  /** Active/désactive une extraction (colonne enabled). */
  toggleBatchEnabled(id: string, enabled: boolean): Observable<Record<string, unknown>> {
    return this.http.patch<Record<string, unknown>>(API.extractor.schedulerToggle(id), { enabled });
  }

  /**
   * Heure de déclenchement quotidien (HH:mm) et bascule on/off.
   * Prise en compte par l'AutoExtractionScheduler en ≤ 1 minute.
   */
  updateBatchScheduler(id: string, scheduler: boolean,
                       schedulerTime: string | null): Observable<Record<string, unknown>> {
    return this.http.patch<Record<string, unknown>>(
      API.extractor.schedulerUpdate(id), { scheduler, schedulerTime });
  }
}