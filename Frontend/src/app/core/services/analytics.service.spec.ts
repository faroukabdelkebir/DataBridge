import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { AnalyticsService } from './analytics.service';
import { API } from '../config/api.config';

describe('AnalyticsService', () => {
  let service: AnalyticsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AnalyticsService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AnalyticsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  /** Les endpoints "par année" partagent tous le param year. */
  it.each([
    ['getSummary', () => service.getSummary(2025).subscribe(), API.monitoring.analytics.summary],
    ['getMonthlyTransfers', () => service.getMonthlyTransfers(2025).subscribe(), API.monitoring.analytics.transfersMonthly],
    ['getInsights', () => service.getInsights(2025).subscribe(), API.monitoring.analytics.insights],
    ['getFlowsSummary', () => service.getFlowsSummary(2025).subscribe(), API.monitoring.analytics.flowsSummary],
    ['getFlowsByCurrency', () => service.getFlowsByCurrency(2025).subscribe(), API.monitoring.analytics.flowsByCurrency],
    ['getFlowsByType', () => service.getFlowsByType(2025).subscribe(), API.monitoring.analytics.flowsByType],
    ['getActivityHeatmap', () => service.getActivityHeatmap(2025).subscribe(), API.monitoring.analytics.flowsHeatmap],
    ['getCustomersSummary', () => service.getCustomersSummary(2025).subscribe(), API.monitoring.analytics.customersSummary],
    ['getBillingSummary', () => service.getBillingSummary(2025).subscribe(), API.monitoring.analytics.billingSummary],
    ['getBillingMonthly', () => service.getBillingMonthly(2025).subscribe(), API.monitoring.analytics.billingMonthly],
  ])('%s : GET avec year=2025', (_name, call, url) => {
    call();
    const req = httpMock.expectOne((r) => r.url === url);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('year')).toBe('2025');
    req.flush({});
  });

  it('getTopOfficers : GET avec top par défaut à 10', () => {
    service.getTopOfficers().subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.analytics.byOfficer);
    expect(req.request.params.get('top')).toBe('10');
    req.flush([]);
  });

  it('getCustomersBreakdown : GET avec le paramètre by', () => {
    service.getCustomersBreakdown('gender').subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.analytics.customersBreakdown);
    expect(req.request.params.get('by')).toBe('gender');
    req.flush([]);
  });

  it('getBillingBreakdown : GET avec by + year', () => {
    service.getBillingBreakdown('currency', 2024).subscribe();
    const req = httpMock.expectOne((r) => r.url === API.monitoring.analytics.billingBreakdown);
    expect(req.request.params.get('by')).toBe('currency');
    expect(req.request.params.get('year')).toBe('2024');
    req.flush([]);
  });

  it('getYears : GET sans paramètre', () => {
    service.getYears().subscribe();
    const req = httpMock.expectOne(API.monitoring.analytics.years);
    expect(req.request.params.keys()).toHaveLength(0);
    req.flush([]);
  });
});
