import { Component, computed, inject, signal } from '@angular/core';
import { UpperCasePipe } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { ButtonModule } from 'primeng/button';
import { AvatarModule } from 'primeng/avatar';
import { MenuModule } from 'primeng/menu';
import { MenuItem } from 'primeng/api';

import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { LanguageService, Lang } from '../../core/services/language.service';

interface NavItem {
  labelKey: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-main-layout',
  imports: [
    RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, UpperCasePipe,
    ButtonModule, AvatarModule, MenuModule,
  ],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  private auth = inject(AuthService);
  private router = inject(Router);
  private translate = inject(TranslateService);
  readonly theme = inject(ThemeService);
  readonly lang = inject(LanguageService);

  readonly user = this.auth.user;
  readonly collapsed = signal(false);

readonly nav = computed<NavItem[]>(() => {
  // Visiteur : accès limité au dashboard, à l'analytics et à l'assistant IA.
  if (this.auth.hasRole('VISITOR')) {
    return [
      { labelKey: 'nav.dashboard',  icon: 'pi pi-th-large',    route: '/dashboard' },
      { labelKey: 'nav.analytics',  icon: 'pi pi-chart-line',  route: '/analytics' },
      { labelKey: 'nav.assistant',  icon: 'pi pi-sparkles',    route: '/assistant' },
    ];
  }

  const items: NavItem[] = [
    { labelKey: 'nav.dashboard',      icon: 'pi pi-th-large',              route: '/dashboard' },
    { labelKey: 'nav.analytics', icon: 'pi pi-chart-line', route: '/analytics' },
    { labelKey: 'nav.jobs',           icon: 'pi pi-history',               route: '/jobs' },
    { labelKey: 'nav.alerts',         icon: 'pi pi-bell',                  route: '/alerts' },
    // { labelKey: 'nav.sla',            icon: 'pi pi-clock',                 route: '/sla' },
    // { labelKey: 'nav.reconciliation', icon: 'pi pi-arrow-right-arrow-left', route: '/reconciliation' },
    { labelKey: 'nav.schema',         icon: 'pi pi-database',              route: '/schema' },
    { labelKey: 'nav.assistant', icon: 'pi pi-sparkles', route: '/assistant' },
  ];
  if (this.auth.hasRole('ADMIN')) {
    items.push({ labelKey: 'nav.batch', icon: 'pi pi-sliders-h', route: '/batch' });
    items.push({ labelKey: 'nav.users', icon: 'pi pi-users', route: '/admin/users' });
  }
  return items;
});

  // Menu profil — recalculé quand la langue change (dépend de currentLang signal)
  readonly profileMenu = computed<MenuItem[]>(() => {
    this.translate.currentLang();
    return [
      {
        label: this.translate.instant('nav.profile'),
        icon: 'pi pi-user',
        command: () => this.router.navigate(['/profile']),
      },
      { separator: true },
      {
        label: this.translate.instant('nav.logout'),
        icon: 'pi pi-sign-out',
        command: () => this.auth.logout(),
      },
    ];
  });

  // Menu de sélection de langue (coche sur la langue active)
  readonly langMenu = computed<MenuItem[]>(() => {
    const active = this.lang.current();
    return this.lang.available.map((l) => ({
      label: l.label,
      icon: l.code === active ? 'pi pi-check' : 'pi pi-globe',
      command: () => this.lang.setLang(l.code),
    }));
  });

  toggle(): void {
    this.collapsed.update((v) => !v);
  }


  currentLangCode(): Lang {
    return this.lang.current();
  }
}
