import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

/**
 * Guard de rôle. Usage dans les routes :
 *   canActivate: [authGuard, roleGuard('ADMIN')]
 *   canActivate: [authGuard, roleGuard('ADMIN', 'MONITOR')]
 */
export function roleGuard(...allowed: Role[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    const router = inject(Router);

    if (auth.hasRole(...allowed)) {
      return true;
    }
    // Connecté mais pas autorisé → retour dashboard
    return router.createUrlTree(['/dashboard']);
  };
}