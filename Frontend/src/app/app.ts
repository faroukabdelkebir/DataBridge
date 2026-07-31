import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Toast } from 'primeng/toast';

import { LanguageService } from './core/services/language.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Toast],
  template: `
    <p-toast position="top-right" />
    <p-toast position="top-right" key="global" />
    <router-outlet />
  `,
})
export class App {
  // Instancié dès le démarrage : applique langue + direction (RTL/LTR) globalement
  private readonly lang = inject(LanguageService);
}
