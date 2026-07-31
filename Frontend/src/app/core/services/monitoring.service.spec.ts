import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { MonitoringService } from './monitoring.service';
import { API } from '../config/api.config';

/**
 * Tests de régression : figent les URLs et paramètres de requête
 * appelés par le service. Toute modification involontaire du contrat
 * HTTP (chemin, verbe, query params) casse un test.
 */
describe('MonitoringService', () => {
  let service: MonitoringService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [MonitoringService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MonitoringService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getRecentJobs : GET /jobs avec le paramètre limit', () => {
    service.getRecentJobs(50).subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.jobs);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('limit')).toBe('50');
    req.flush([]);
  });

  it('getRecentJobs : limit par défaut à 20', () => {
    service.getRecentJobs().subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.jobs);
    expect(req.request.params.get('limit')).toBe('20');
    req.flush([]);
  });

  it('getJobsHistory : GET /jobs/history avec le paramètre days', () => {
    service.getJobsHistory(14).subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.jobsHistory);
    expect(req.request.params.get('days')).toBe('14');
    req.flush([]);
  });

  it('getJobById : GET /jobs/{id}', () => {
    service.getJobById(99).subscribe();
    const req = httpMock.expectOne(API.monitoring.jobById(99));
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getJobSteps : GET /jobs/{id}/steps', () => {
    service.getJobSteps(7).subscribe();
    httpMock.expectOne(API.monitoring.jobSteps(7)).flush([]);
  });

  it('getKpis : GET /kpis avec days', () => {
    service.getKpis(30).subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.kpis);
    expect(req.request.params.get('days')).toBe('30');
    req.flush({});
  });

  it('resolveAlert : DELETE sur l’URL encodée', () => {
    service.resolveAlert('key with space').subscribe();
    const req = httpMock.expectOne(API.monitoring.resolveAlert('key with space'));
    expect(req.request.method).toBe('DELETE');
    expect(req.request.url).toContain('key%20with%20space');
    req.flush({});
  });

  it('getSummary : GET /dashboard/summary', () => {
    service.getSummary().subscribe();
    const req = httpMock.expectOne(API.monitoring.dashboardSummary);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('getSchemaAudit : GET /schema/audit avec limit par défaut à 50', () => {
    service.getSchemaAudit().subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.schemaAudit);
    expect(req.request.params.get('limit')).toBe('50');
    req.flush([]);
  });
});
