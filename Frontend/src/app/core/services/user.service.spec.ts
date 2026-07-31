import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { UserService } from './user.service';
import { API } from '../config/api.config';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [UserService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('search', () => {
    it('n’envoie que les paramètres de pagination par défaut', () => {
      service.search({}).subscribe();
      const req = httpMock.expectOne((r) => r.url === API.users.base);
      expect(req.request.params.get('page')).toBe('0');
      expect(req.request.params.get('size')).toBe('10');
      expect(req.request.params.has('search')).toBe(false);
      expect(req.request.params.has('role')).toBe(false);
      expect(req.request.params.has('active')).toBe(false);
      req.flush({ content: [] });
    });

    it('ajoute les filtres fournis, y compris active=false', () => {
      service.search({ search: 'ali', role: 'ADMIN', active: false }, 2, 25).subscribe();
      const req = httpMock.expectOne((r) => r.url === API.users.base);
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('25');
      expect(req.request.params.get('search')).toBe('ali');
      expect(req.request.params.get('role')).toBe('ADMIN');
      expect(req.request.params.get('active')).toBe('false');
      req.flush({ content: [] });
    });
  });

  it('getById : GET /users/{id}', () => {
    service.getById('u1').subscribe();
    const req = httpMock.expectOne(API.users.byId('u1'));
    expect(req.request.method).toBe('GET');
    req.flush({});
  });

  it('create : POST /users avec le payload', () => {
    const payload = { username: 'bob', email: 'b@x', password: 'Aa1@aaaa', role: 'MONITOR' as const };
    service.create(payload).subscribe();
    const req = httpMock.expectOne(API.users.base);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({});
  });

  it('update : PATCH /users/{id}', () => {
    service.update('u1', { active: true }).subscribe();
    const req = httpMock.expectOne(API.users.byId('u1'));
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ active: true });
    req.flush({});
  });

  it('activate / deactivate : PATCH sur les sous-ressources', () => {
    service.activate('u1').subscribe();
    httpMock.expectOne((r) => r.url === API.users.activate('u1') && r.method === 'PATCH').flush(null);

    service.deactivate('u1').subscribe();
    httpMock.expectOne((r) => r.url === API.users.deactivate('u1') && r.method === 'PATCH').flush(null);
  });

  it('delete : DELETE /users/{id}', () => {
    service.delete('u1').subscribe();
    const req = httpMock.expectOne(API.users.byId('u1'));
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
