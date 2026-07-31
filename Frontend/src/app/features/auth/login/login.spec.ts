import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';

import { Login } from './login';
import { AuthService } from '../../../core/services/auth.service';

describe('Login (composant)', () => {
  let auth: { login: ReturnType<typeof vi.fn> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };
  let toast: { add: ReturnType<typeof vi.fn> };
  let queryParams: Record<string, string | null>;

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Login,
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: toast },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: (k: string) => queryParams[k] ?? null } } },
        },
      ],
    });
    return TestBed.inject(Login);
  }

  beforeEach(() => {
    queryParams = {};
    auth = { login: vi.fn().mockReturnValue(of({ username: 'alice' })) };
    router = { navigateByUrl: vi.fn() };
    toast = { add: vi.fn() };
  });

  it('affiche une erreur "session expirée" via le query param', () => {
    queryParams = { expired: '1' };
    const comp = create();
    expect(comp.error()).toBe('auth.sessionExpired');
  });

  it('form invalide au départ (champs vides)', () => {
    const comp = create();
    expect(comp.form.invalid).toBe(true);
  });

  it('ne soumet pas un formulaire invalide', () => {
    const comp = create();
    comp.submit();
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('valide avec des identifiants corrects', () => {
    const comp = create();
    comp.form.setValue({ username: 'alice', password: 'password1' });
    expect(comp.form.valid).toBe(true);
  });

  it('sur succès : toast + redirection vers returnUrl', () => {
    queryParams = { returnUrl: '/jobs' };
    const comp = create();
    comp.form.setValue({ username: 'alice', password: 'password1' });

    comp.submit();

    expect(auth.login).toHaveBeenCalledWith({ username: 'alice', password: 'password1' });
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/jobs');
  });

  it('sur succès sans returnUrl : redirige vers /dashboard', () => {
    const comp = create();
    comp.form.setValue({ username: 'alice', password: 'password1' });
    comp.submit();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('sur 401 : message d’identifiants invalides', () => {
    auth.login.mockReturnValue(throwError(() => ({ status: 401 })));
    const comp = create();
    comp.form.setValue({ username: 'alice', password: 'password1' });

    comp.submit();

    expect(comp.error()).toBe('auth.login.error');
    expect(comp.loading()).toBe(false);
  });

  it('sur autre erreur : message serveur', () => {
    auth.login.mockReturnValue(throwError(() => ({ status: 500 })));
    const comp = create();
    comp.form.setValue({ username: 'alice', password: 'password1' });

    comp.submit();

    expect(comp.error()).toBe('auth.login.serverError');
  });
});
