import { inject, Injector } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { MessageService } from 'primeng/api';
import { TranslateService } from '@ngx-translate/core';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(MessageService);
  // Résolution paresseuse : injecter TranslateService ici créerait une
  // dépendance circulaire (son HttpLoader passe par cet interceptor pendant
  // sa propre construction → NG0200). On le récupère seulement à l'erreur.
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const t = (k: string) => injector.get(TranslateService).instant(k);

      if (err.status === 0) {
        // Backend/Kong injoignable
        toast.add({
          severity: 'error', key: 'global',
          summary: t('errors.networkTitle'),
          detail: t('errors.network'),
          life: 6000,
        });
      } else if (err.status === 403) {
        toast.add({
          severity: 'warn', key: 'global',
          summary: t('errors.forbiddenTitle'),
          detail: t('errors.forbidden'),
        });
      } else if (err.status >= 500) {
        toast.add({
          severity: 'error', key: 'global',
          summary: t('errors.serverTitle'),
          detail: err.error?.message ?? t('errors.server'),
          life: 6000,
        });
      } else if (err.status === 429) {
        toast.add({
          severity: 'warn', key: 'global',
          summary: t('errors.rateLimitTitle'),
          detail: t('errors.rateLimit'),
          life: 6000,
        });
      }
      // 401 → authInterceptor ; 400/404/409 → composants
      return throwError(() => err);
    }),
  );
};