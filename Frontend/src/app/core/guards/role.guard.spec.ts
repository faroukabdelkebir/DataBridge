import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, RouterStateSnapshot, ActivatedRouteSnapshot } from '@angular/router';

import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

describe('roleGuard', () => {
  let currentRoles: Role[];

  const routeStub = {} as ActivatedRouteSnapshot;
  const stateStub = { url: '/admin' } as RouterStateSnapshot;

  function run(...allowed: Role[]) {
    const guard = roleGuard(...allowed);
    return TestBed.runInInjectionContext(() => guard(routeStub, stateStub));
  }

  beforeEach(() => {
    currentRoles = [];
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AuthService,
          useValue: {
            hasRole: (...roles: Role[]) => roles.some((r) => currentRoles.includes(r)),
          },
        },
      ],
    });
  });

  it('autorise l’accès quand le rôle est permis', () => {
    currentRoles = ['ADMIN'];
    expect(run('ADMIN')).toBe(true);
  });

  it('accepte l’un des rôles autorisés', () => {
    currentRoles = ['MONITOR'];
    expect(run('ADMIN', 'MONITOR')).toBe(true);
  });

  it('redirige vers /dashboard quand le rôle n’est pas permis', () => {
    currentRoles = ['VISITOR'];
    const result = run('ADMIN') as UrlTree;

    expect(result).toBeInstanceOf(UrlTree);
    expect(result.toString()).toBe(TestBed.inject(Router).createUrlTree(['/dashboard']).toString());
  });
});
