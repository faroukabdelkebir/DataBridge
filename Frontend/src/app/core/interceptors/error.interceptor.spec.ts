import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { MessageService } from 'primeng/api';
import { TranslateService } from '@ngx-translate/core';

import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let toast: { add: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    toast = { add: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MessageService, useValue: toast },
        // instant renvoie la clé i18n telle quelle → assertions lisibles.
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function trigger(status: number, errorBody: string | object = 'err') {
    let caught: { status?: number } | undefined;
    http.get('/api/x').subscribe({ error: (e) => (caught = e) });
    httpMock.expectOne('/api/x').flush(errorBody, { status, statusText: 'x' });
    return () => caught;
  }

  it('status 0 (réseau) → toast d’erreur réseau', () => {
    trigger(0);
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'errors.network' }),
    );
  });

  it('status 403 → toast d’avertissement interdit', () => {
    trigger(403);
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'warn', detail: 'errors.forbidden' }),
    );
  });

  it('status 500 → toast serveur avec message du backend s’il existe', () => {
    trigger(500, { message: 'DB down' });
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'DB down' }),
    );
  });

  it('status 500 sans message → détail traduit par défaut', () => {
    trigger(500, 'plain');
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', detail: 'errors.server' }),
    );
  });

  it('status 429 → toast de limitation de débit', () => {
    trigger(429);
    expect(toast.add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'warn', detail: 'errors.rateLimit' }),
    );
  });

  it('status 401 → aucun toast (géré par authInterceptor)', () => {
    trigger(401);
    expect(toast.add).not.toHaveBeenCalled();
  });

  it('status 404 → aucun toast (géré par le composant)', () => {
    trigger(404);
    expect(toast.add).not.toHaveBeenCalled();
  });

  it('propage toujours l’erreur en aval', () => {
    const getCaught = trigger(500);
    expect(getCaught()?.status).toBe(500);
  });
});
