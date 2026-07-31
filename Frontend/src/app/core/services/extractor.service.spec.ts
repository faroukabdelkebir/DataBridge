import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { ExtractorService } from './extractor.service';
import { API } from '../config/api.config';

describe('ExtractorService', () => {
  let service: ExtractorService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ExtractorService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ExtractorService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('startMain : POST vide sur l’URL de démarrage T24', () => {
    service.startMain().subscribe();
    const req = httpMock.expectOne(API.extractor.startMain);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush(null);
  });

  it('startCustomer / startFtHis / startBillDetails / stopMain : bonnes URLs', () => {
    const cases: [() => void, string][] = [
      [() => service.startCustomer().subscribe(), API.extractor.startCustomer],
      [() => service.startFtHis().subscribe(), API.extractor.startFtHis],
      [() => service.startBillDetails().subscribe(), API.extractor.startBills],
      [() => service.stopMain().subscribe(), API.extractor.stopMain],
    ];
    for (const [call, url] of cases) {
      call();
      const req = httpMock.expectOne(url);
      expect(req.request.method).toBe('POST');
      req.flush(null);
    }
  });

  it('getColumns : GET avec table encodée', () => {
    service.getColumns('T24 TABLE').subscribe();
    const req = httpMock.expectOne(API.extractor.schemaColumns('T24 TABLE'));
    expect(req.request.method).toBe('GET');
    expect(req.request.url).toContain('T24%20TABLE');
    req.flush([]);
  });

  it('setColumnEnabled : PATCH avec params + body enabled', () => {
    service.setColumnEnabled('TBL', 'COL', true).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === API.extractor.toggleColumn && r.method === 'PATCH',
    );
    expect(req.request.params.get('tableName')).toBe('TBL');
    expect(req.request.params.get('columnName')).toBe('COL');
    expect(req.request.body).toEqual({ enabled: true });
    req.flush({});
  });

  it('toggleBatchEnabled : PATCH sur l’URL encodée avec body', () => {
    service.toggleBatchEnabled('id 1', false).subscribe();
    const req = httpMock.expectOne(API.extractor.schedulerToggle('id 1'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ enabled: false });
    req.flush({});
  });

  it('updateBatchScheduler : PATCH avec scheduler + heure', () => {
    service.updateBatchScheduler('b1', true, '02:30').subscribe();
    const req = httpMock.expectOne(API.extractor.schedulerUpdate('b1'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ scheduler: true, schedulerTime: '02:30' });
    req.flush({});
  });
});
