import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService, SKIP_AUTH_REFRESH } from '../services/auth.service';

/**
 * 1. Pose le Bearer token sur toute requête sortante.
 * 2. Sur 401 : tente UN refresh (partagé entre requêtes concurrentes),
 *    rejoue la requête avec le nouveau token.
 * 3. Si le refresh échoue : purge la session et redirige vers /login.
 *
 * Les requêtes marquées SKIP_AUTH_REFRESH (/login, /refresh,
 * /forgot-password, /reset-password) ne déclenchent jamais de refresh
 * — c'est ce qui empêche toute boucle infinie.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);

  const authedReq = withBearer(req, auth.accessToken);

  return next(authedReq).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthError = err.status === 401;
      const canRefresh =
        !req.context.get(SKIP_AUTH_REFRESH) && auth.refreshToken !== null;

      if (!isAuthError || !canRefresh) {
        return throwError(() => err);
      }

      // 401 sur une requête "normale" → refresh puis rejeu
      return auth.refreshSession().pipe(
        catchError((refreshErr) => {
          // SEUL l'échec du refresh termine la session
          auth.clearSession();
          window.location.href = '/login';
          return throwError(() => refreshErr);
        }),
        switchMap(() => next(withBearer(req, auth.accessToken))),
        // un 401 du rejeu remonte tel quel au composant
      );
    }),
  );
};

function withBearer(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  if (!token) return req;
  return req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
}