import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { UrlTree, RouterStateSnapshot, ActivatedRouteSnapshot } from '@angular/router';

import { authGuard } from './auth.guard';
import { AuthService } from '../services/auth.service';

describe('authGuard', () => {
  let isAuthenticated: boolean;

  const routeStub = {} as ActivatedRouteSnapshot;
  const stateStub = (url: string) => ({ url }) as RouterStateSnapshot;

  /** Exécute le guard dans un contexte d'injection Angular. */
  function run(url = '/dashboard') {
    return TestBed.runInInjectionContext(() =>
      authGuard(routeStub, stateStub(url)),
    );
  }

  beforeEach(() => {
    isAuthenticated = false;
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: () => isAuthenticated } },
      ],
    });
  });

  it('laisse passer un utilisateur authentifié', () => {
    isAuthenticated = true;
    expect(run()).toBe(true);
  });

  it('redirige vers /login en conservant l’URL de retour', () => {
    isAuthenticated = false;
    const result = run('/jobs') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toContain('/login');
    expect(result.queryParams['returnUrl']).toBe('/jobs');
  });
});
