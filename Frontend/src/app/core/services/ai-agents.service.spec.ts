import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { AiAgentsService } from './ai-agents.service';
import { API } from '../config/api.config';

describe('AiAgentsService', () => {
  let service: AiAgentsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AiAgentsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AiAgentsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getHealth : GET /aiagents/', () => {
    service.getHealth().subscribe();
    const req = httpMock.expectOne(API.aiAgents.health);
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('quickScore : POST avec table_name + model (snake_case backend)', () => {
    service.quickScore('CUSTOMERS', 'llama3').subscribe();
    const req = httpMock.expectOne(API.aiAgents.quickScore);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ table_name: 'CUSTOMERS', model: 'llama3' });
    req.flush({});
  });

  it('audit : POST avec table_name + model', () => {
    service.audit('BILLS', 'mistral').subscribe();
    const req = httpMock.expectOne(API.aiAgents.audit);
    expect(req.request.body).toEqual({ table_name: 'BILLS', model: 'mistral' });
    req.flush({});
  });

  it('auditAll : POST avec le seul model', () => {
    service.auditAll('llama3').subscribe();
    const req = httpMock.expectOne(API.aiAgents.auditAll);
    expect(req.request.body).toEqual({ model: 'llama3' });
    req.flush({ status: 'ok', report_id: 1, report: {} });
  });

  it('launchRecommendation : POST avec refresh par défaut à false', () => {
    service.launchRecommendation('llama3').subscribe();
    const req = httpMock.expectOne(API.aiAgents.schedulerRecommend);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ model: 'llama3', refresh: false });
    req.flush({});
  });

  it('getRecommendation : GET /scheduler/recommend/', () => {
    service.getRecommendation().subscribe();
    httpMock.expectOne((r) => r.url === API.aiAgents.schedulerRecommend && r.method === 'GET').flush({});
  });

  it('shouldLaunch : POST avec batch_id', () => {
    service.shouldLaunch('B1').subscribe();
    const req = httpMock.expectOne(API.aiAgents.shouldLaunch);
    expect(req.request.body).toEqual({ batch_id: 'B1' });
    req.flush({});
  });

  it('getReport : GET /reports/{id}/', () => {
    service.getReport(42).subscribe();
    const req = httpMock.expectOne(API.aiAgents.reportById(42));
    expect(req.request.method).toBe('GET');
    req.flush({});
  });
});
