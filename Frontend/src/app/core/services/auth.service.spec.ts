import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { AuthService } from './auth.service';
import { API } from '../config/api.config';
import { AuthResponse } from '../models/auth.model';

const AUTH_RESPONSE: AuthResponse = {
  accessToken: 'access-123',
  refreshToken: 'refresh-456',
  tokenType: 'Bearer',
  expiresIn: 3600,
  issuedAt: '2026-07-28T10:00:00Z',
  userId: 'uuid-1',
  username: 'alice',
  email: 'alice@example.com',
  role: 'ADMIN',
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    service.clearSession(); // stoppe le timer de surveillance de session
  });

  it('démarre déconnecté quand localStorage est vide', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.role()).toBeNull();
    expect(service.accessToken).toBeNull();
  });

  describe('login', () => {
    it('persiste la session et expose l’utilisateur', () => {
      let emitted: unknown;
      service.login({ username: 'alice', password: 'secret' }).subscribe((u) => (emitted = u));

      const req = httpMock.expectOne(API.auth.login);
      expect(req.request.method).toBe('POST');
      req.flush(AUTH_RESPONSE);

      expect(service.isAuthenticated()).toBe(true);
      expect(service.role()).toBe('ADMIN');
      expect(service.accessToken).toBe('access-123');
      expect(service.refreshToken).toBe('refresh-456');
      expect(service.user()?.username).toBe('alice');
      expect(emitted).toMatchObject({ username: 'alice', role: 'ADMIN' });
    });
  });

  describe('hasRole', () => {
    beforeEach(() => {
      service.login({ username: 'alice', password: 'x' }).subscribe();
      httpMock.expectOne(API.auth.login).flush(AUTH_RESPONSE);
    });

    it('retourne true quand le rôle courant est autorisé', () => {
      expect(service.hasRole('ADMIN')).toBe(true);
      expect(service.hasRole('MONITOR', 'ADMIN')).toBe(true);
    });

    it('retourne false quand le rôle courant n’est pas autorisé', () => {
      expect(service.hasRole('MONITOR')).toBe(false);
      expect(service.hasRole('VISITOR')).toBe(false);
    });
  });

  describe('isAccessTokenExpired', () => {
    it('est vrai sans date d’expiration enregistrée', () => {
      expect(service.isAccessTokenExpired()).toBe(true);
    });

    it('tient compte de la marge de sécurité', () => {
      vi.spyOn(Date, 'now').mockReturnValue(1_000_000);
      // Expiration dans 10 s ; marge 15 s => déjà considéré expiré.
      localStorage.setItem('bi_expires_at', String(1_000_000 + 10_000));
      expect(service.isAccessTokenExpired(15)).toBe(true);
      // Expiration dans 60 s ; marge 15 s => encore valide.
      localStorage.setItem('bi_expires_at', String(1_000_000 + 60_000));
      expect(service.isAccessTokenExpired(15)).toBe(false);
    });
  });

  describe('logout', () => {
    it('révoque le refresh token, purge la session et redirige', () => {
      service.login({ username: 'alice', password: 'x' }).subscribe();
      httpMock.expectOne(API.auth.login).flush(AUTH_RESPONSE);

      service.logout();

      const req = httpMock.expectOne(API.auth.logout);
      expect(req.request.body).toEqual({ refreshToken: 'refresh-456' });
      req.flush({ message: 'ok' });

      expect(service.isAuthenticated()).toBe(false);
      expect(localStorage.getItem('bi_access_token')).toBeNull();
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('refreshSession', () => {
    it('échoue sans refresh token disponible', () => {
      let error: Error | undefined;
      service.refreshSession().subscribe({ error: (e) => (error = e) });
      expect(error?.message).toBe('NO_REFRESH_TOKEN');
    });

    it('partage une seule requête HTTP entre appels concurrents', () => {
      localStorage.setItem('bi_refresh_token', 'refresh-456');

      service.refreshSession().subscribe();
      service.refreshSession().subscribe();

      // Une seule requête réseau malgré deux appels (shareReplay).
      const req = httpMock.expectOne(API.auth.refresh);
      req.flush(AUTH_RESPONSE);

      expect(service.accessToken).toBe('access-123');
    });
  });

  describe('initSession', () => {
    it('renvoie null sans aucun token', () => {
      let result: unknown = 'unset';
      service.initSession().subscribe((r) => (result = r));
      expect(result).toBeNull();
      httpMock.expectNone(API.auth.me);
    });

    it('purge la session sur 401', () => {
      localStorage.setItem('bi_access_token', 'stale');
      let result: unknown = 'unset';
      service.initSession().subscribe((r) => (result = r));

      httpMock.expectOne(API.auth.me).flush('nope', { status: 401, statusText: 'Unauthorized' });

      expect(result).toBeNull();
      expect(localStorage.getItem('bi_access_token')).toBeNull();
    });

    it('conserve la session locale sur erreur serveur (5xx)', () => {
      localStorage.setItem('bi_access_token', 'stale');
      localStorage.setItem(
        'bi_user',
        JSON.stringify({ id: 'u', username: 'bob', email: 'b@x', role: 'MONITOR', fullName: 'bob' }),
      );

      let result: { username?: string } | null = null;
      service.initSession().subscribe((r) => (result = r as { username?: string } | null));
      httpMock.expectOne(API.auth.me).flush('boom', { status: 503, statusText: 'Unavailable' });

      expect(result).not.toBeNull();
      expect(result!.username).toBe('bob');
    });
  });
});
