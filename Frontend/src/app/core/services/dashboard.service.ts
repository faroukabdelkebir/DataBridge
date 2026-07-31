import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, delay } from 'rxjs';

import { environment } from '../../../environments/environment';
import { Kpi, TimeSeriesPoint, CategorySlice } from '../models/kpi.model';
import { Transaction } from '../models/transaction.model';
import {
  MOCK_KPIS,
  MOCK_REVENUE_SERIES,
  MOCK_ORDERS_SERIES,
  MOCK_CATEGORIES,
  MOCK_TRANSACTIONS,
} from '../mocks/mock-data';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private base = `${environment.gatewayUrl}/dashboard`;

  getKpis(): Observable<Kpi[]> {
    return environment.useMock
      ? of(MOCK_KPIS).pipe(delay(400))
      : this.http.get<Kpi[]>(`${this.base}/kpis`);
  }

  getRevenueSeries(): Observable<TimeSeriesPoint[]> {
    return environment.useMock
      ? of(MOCK_REVENUE_SERIES).pipe(delay(400))
      : this.http.get<TimeSeriesPoint[]>(`${this.base}/revenue`);
  }

  getOrdersSeries(): Observable<TimeSeriesPoint[]> {
    return environment.useMock
      ? of(MOCK_ORDERS_SERIES).pipe(delay(400))
      : this.http.get<TimeSeriesPoint[]>(`${this.base}/orders`);
  }

  getCategoryBreakdown(): Observable<CategorySlice[]> {
    return environment.useMock
      ? of(MOCK_CATEGORIES).pipe(delay(400))
      : this.http.get<CategorySlice[]>(`${this.base}/categories`);
  }

  getTransactions(): Observable<Transaction[]> {
    return environment.useMock
      ? of(MOCK_TRANSACTIONS).pipe(delay(500))
      : this.http.get<Transaction[]>(`${this.base}/transactions`);
  }
}
