import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { API } from '../config/api.config';
import {
  AnalyticsSummary, BillingSummary, BreakdownSlice, CustomersSummary, FlowSlice,
  FlowsSummary, HeatmapCell, Insight, MonthlyFlow, OfficerPerf,
} from '../models/analytics.model';

/** Accès aux endpoints /api/v1/analytics du MonitoringMS (via Kong). (v4) */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private http = inject(HttpClient);

  private byYear(year: number): { params: HttpParams } {
    return { params: new HttpParams().set('year', year) };
  }

  // ── Page Synthèse ──────────────────────────────────────────

  getSummary(year: number): Observable<AnalyticsSummary> {
    return this.http.get<AnalyticsSummary>(API.monitoring.analytics.summary, this.byYear(year));
  }

  getMonthlyTransfers(year: number): Observable<MonthlyFlow[]> {
    return this.http.get<MonthlyFlow[]>(API.monitoring.analytics.transfersMonthly, this.byYear(year));
  }

  getAgePyramid(): Observable<BreakdownSlice[]> {
    return this.http.get<BreakdownSlice[]>(API.monitoring.analytics.agePyramid);
  }

  getTopOfficers(top = 10): Observable<OfficerPerf[]> {
    return this.http.get<OfficerPerf[]>(API.monitoring.analytics.byOfficer, {
      params: new HttpParams().set('top', top),
    });
  }

  getBillingAging(): Observable<BreakdownSlice[]> {
    return this.http.get<BreakdownSlice[]>(API.monitoring.analytics.billingAging);
  }

  getYears(): Observable<number[]> {
    return this.http.get<number[]>(API.monitoring.analytics.years);
  }

  getInsights(year: number): Observable<Insight[]> {
    return this.http.get<Insight[]>(API.monitoring.analytics.insights, this.byYear(year));
  }

  // ── Page Flux ──────────────────────────────────────────────

  getFlowsSummary(year: number): Observable<FlowsSummary> {
    return this.http.get<FlowsSummary>(API.monitoring.analytics.flowsSummary, this.byYear(year));
  }

  getFlowsByCurrency(year: number): Observable<FlowSlice[]> {
    return this.http.get<FlowSlice[]>(API.monitoring.analytics.flowsByCurrency, this.byYear(year));
  }

  getFlowsByType(year: number): Observable<FlowSlice[]> {
    return this.http.get<FlowSlice[]>(API.monitoring.analytics.flowsByType, this.byYear(year));
  }

  getActivityHeatmap(year: number): Observable<HeatmapCell[]> {
    return this.http.get<HeatmapCell[]>(API.monitoring.analytics.flowsHeatmap, this.byYear(year));
  }

  getFlowsColumnsStatus(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(API.monitoring.analytics.flowsColumnsStatus);
  }

  getFlowsInsights(year: number): Observable<Insight[]> {
    return this.http.get<Insight[]>(API.monitoring.analytics.flowsInsights, this.byYear(year));
  }

  // ── Page Clients ───────────────────────────────────────────

  getCustomersSummary(year: number): Observable<CustomersSummary> {
    return this.http.get<CustomersSummary>(API.monitoring.analytics.customersSummary, this.byYear(year));
  }

  getAgeHistogram(): Observable<BreakdownSlice[]> {
    return this.http.get<BreakdownSlice[]>(API.monitoring.analytics.customersAgeHistogram);
  }

  getGenerations(): Observable<BreakdownSlice[]> {
    return this.http.get<BreakdownSlice[]>(API.monitoring.analytics.customersGenerations);
  }

  getNewCustomersMonthly(year: number): Observable<MonthlyFlow[]> {
    return this.http.get<MonthlyFlow[]>(API.monitoring.analytics.customersNewMonthly, this.byYear(year));
  }

  getCustomersBreakdown(by: string): Observable<BreakdownSlice[]> {
    return this.http.get<BreakdownSlice[]>(API.monitoring.analytics.customersBreakdown, {
      params: new HttpParams().set('by', by),
    });
  }

  getCustomerDimensions(): Observable<string[]> {
    return this.http.get<string[]>(API.monitoring.analytics.customersDimensions);
  }

  getCustomersColumnsStatus(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(API.monitoring.analytics.customersColumnsStatus);
  }

  getAcquisitionYears(): Observable<number[]> {
    return this.http.get<number[]>(API.monitoring.analytics.customersAcquisitionYears);
  }

  getCustomersInsights(year: number): Observable<Insight[]> {
    return this.http.get<Insight[]>(API.monitoring.analytics.customersInsights, this.byYear(year));
  }

  // ── Page Créances ──────────────────────────────────────────

  getBillingSummary(year: number): Observable<BillingSummary> {
    return this.http.get<BillingSummary>(API.monitoring.analytics.billingSummary, this.byYear(year));
  }

  getBillingMonthly(year: number): Observable<MonthlyFlow[]> {
    return this.http.get<MonthlyFlow[]>(API.monitoring.analytics.billingMonthly, this.byYear(year));
  }

  getBillingBreakdown(by: string, year: number): Observable<FlowSlice[]> {
    return this.http.get<FlowSlice[]>(API.monitoring.analytics.billingBreakdown, {
      params: new HttpParams().set('by', by).set('year', year),
    });
  }

  getBillingDimensions(): Observable<string[]> {
    return this.http.get<string[]>(API.monitoring.analytics.billingDimensions);
  }

  getBillingColumnsStatus(): Observable<Record<string, boolean>> {
    return this.http.get<Record<string, boolean>>(API.monitoring.analytics.billingColumnsStatus);
  }

  getBillYears(): Observable<number[]> {
    return this.http.get<number[]>(API.monitoring.analytics.billingYears);
  }

  getBillingInsights(year: number): Observable<Insight[]> {
    return this.http.get<Insight[]>(API.monitoring.analytics.billingInsights, this.byYear(year));
  }
}
