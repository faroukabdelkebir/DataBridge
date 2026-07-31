import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors, HttpContext } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { of, throwError } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AuthService, SKIP_AUTH_REFRESH } from '../services/auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: {
    accessToken: string | null;
    refreshToken: string | null;
    refreshSession: ReturnType<typeof vi.fn>;
    clearSession: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    auth = {
      accessToken: 'token-1',
      refreshToken: 'refresh-1',
      refreshSession: vi.fn(),
      clearSession: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('ajoute l’en-tête Bearer quand un token est présent', () => {
    http.get('/api/data').subscribe();
    const req = httpMock.expectOne('/api/data');
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-1');
    req.flush({});
  });

  it('n’ajoute pas d’en-tête sans token', () => {
    auth.accessToken = null;
    http.get('/api/data').subscribe();
    const req = httpMock.expectOne('/api/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('sur 401 : rafraîchit puis rejoue la requête avec le nouveau token', () => {
    auth.refreshSession.mockImplementation(() => {
      auth.accessToken = 'token-2';
      return of({ accessToken: 'token-2' });
    });

    let body: unknown;
    http.get('/api/data').subscribe((r) => (body = r));

    // 1re tentative → 401
    httpMock.expectOne('/api/data').flush('nope', { status: 401, statusText: 'Unauthorized' });

    // Rejeu avec le token rafraîchi
    const retry = httpMock.expectOne('/api/data');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer token-2');
    retry.flush({ ok: true });

    expect(auth.refreshSession).toHaveBeenCalledTimes(1);
    expect(body).toEqual({ ok: true });
  });

  it('ne rafraîchit pas si la requête porte le contexte SKIP_AUTH_REFRESH', () => {
    let error: unknown;
    http
      .get('/authms/login', { context: new HttpContext().set(SKIP_AUTH_REFRESH, true) })
      .subscribe({ error: (e) => (error = e) });

    httpMock.expectOne('/authms/login').flush('bad', { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(error).toBeTruthy();
  });

  it('ne rafraîchit pas en l’absence de refresh token', () => {
    auth.refreshToken = null;
    let error: unknown;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });

    httpMock.expectOne('/api/data').flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(error).toBeTruthy();
  });

  it('purge la session quand le refresh échoue', () => {
    // Neutralise la navigation (jsdom).
    const original = window.location;
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: { href: '' },
    });
    auth.refreshSession.mockReturnValue(throwError(() => new Error('refresh failed')));

    http.get('/api/data').subscribe({ error: () => undefined });
    httpMock.expectOne('/api/data').flush('nope', { status: 401, statusText: 'Unauthorized' });

    expect(auth.clearSession).toHaveBeenCalledTimes(1);
    expect(window.location.href).toBe('/login');

    Object.defineProperty(window, 'location', { configurable: true, value: original });
  });

  it('propage sans refresh les erreurs non-401', () => {
    let error: { status?: number } | undefined;
    http.get('/api/data').subscribe({ error: (e) => (error = e) });
    httpMock.expectOne('/api/data').flush('boom', { status: 500, statusText: 'Server Error' });

    expect(auth.refreshSession).not.toHaveBeenCalled();
    expect(error?.status).toBe(500);
  });
});
