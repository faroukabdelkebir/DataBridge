import {
  ApplicationConfig,
  provideZonelessChangeDetection,
  provideBrowserGlobalErrorListeners,
  provideAppInitializer,
  inject,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeuix/themes/aura';
import { provideEchartsCore } from 'ngx-echarts';
import { provideTranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader } from '@ngx-translate/http-loader';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { AuthService } from './core/services/auth.service';
import { errorInterceptor } from '@core/interceptors/error.interceptor';
import { MessageService } from 'primeng/api';


export const appConfig: ApplicationConfig = {
  providers: [
    // Angular 21 : zoneless par défaut
    provideZonelessChangeDetection(),
    provideBrowserGlobalErrorListeners(),

    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    MessageService, 

        // Valide la session (GET /me) avant l'affichage si un token existe.
    // Si le token est expiré, l'interceptor tente le refresh automatiquement.
    provideAppInitializer(() => {
      const auth = inject(AuthService);
      return firstValueFrom(auth.initSession());
    }),

    // i18n runtime (fr / en / ar) — fichiers dans public/i18n/*.json
    provideTranslateService({
      lang: 'fr',
      fallbackLang: 'fr',
      loader: provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
    }),

    // PrimeNG v21 (thème Aura, mode "styled")
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.app-dark',
        },
      },
    }),

    // ECharts chargé en lazy pour alléger le bundle initial
    provideEchartsCore({ echarts: () => import('echarts') }),
  ],
};
