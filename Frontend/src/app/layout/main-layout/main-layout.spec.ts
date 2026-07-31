import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';

import { MainLayout } from './main-layout';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { LanguageService } from '../../core/services/language.service';

describe('MainLayout (composant)', () => {
  let roles: string[];
  let auth: { user: unknown; hasRole: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let lang: { current: ReturnType<typeof signal<string>>; available: { code: string; label: string }[]; setLang: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  function create() {
    TestBed.configureTestingModule({
      providers: [
        MainLayout,
        { provide: AuthService, useValue: auth },
        { provide: ThemeService, useValue: { darkMode: signal(false), toggle: vi.fn() } },
        { provide: LanguageService, useValue: lang },
        { provide: Router, useValue: router },
        { provide: TranslateService, useValue: { instant: (k: string) => k, currentLang: () => 'fr' } },
      ],
    });
    return TestBed.inject(MainLayout);
  }

  beforeEach(() => {
    roles = [];
    auth = {
      user: signal(null),
      hasRole: vi.fn((...r: string[]) => r.some((x) => roles.includes(x))),
      logout: vi.fn(),
    };
    lang = {
      current: signal('fr'),
      available: [
        { code: 'fr', label: 'Français' },
        { code: 'en', label: 'English' },
        { code: 'ar', label: 'العربية' },
      ],
      setLang: vi.fn(),
    };
    router = { navigate: vi.fn() };
  });

  describe('nav (selon le rôle)', () => {
    it('VISITOR : accès limité (dashboard, analytics, assistant)', () => {
      roles = ['VISITOR'];
      const comp = create();
      expect(comp.nav().map((n) => n.route)).toEqual(['/dashboard', '/analytics', '/assistant']);
    });

    it('MONITOR : jobs et alerts, mais pas les pages admin', () => {
      roles = ['MONITOR'];
      const comp = create();
      const routes = comp.nav().map((n) => n.route);
      expect(routes).toContain('/jobs');
      expect(routes).toContain('/alerts');
      expect(routes).not.toContain('/batch');
      expect(routes).not.toContain('/admin/users');
    });

    it('ADMIN : ajoute batch et admin/users', () => {
      roles = ['ADMIN'];
      const comp = create();
      const routes = comp.nav().map((n) => n.route);
      expect(routes).toContain('/batch');
      expect(routes).toContain('/admin/users');
    });
  });

  it('langMenu marque la langue active d’une coche', () => {
    const comp = create();
    lang.current.set('en');
    const menu = comp.langMenu();
    const en = menu.find((m) => m.label === 'English');
    const fr = menu.find((m) => m.label === 'Français');
    expect(en?.icon).toBe('pi pi-check');
    expect(fr?.icon).toBe('pi pi-globe');
  });

  it('langMenu déclenche setLang au clic', () => {
    const comp = create();
    comp.langMenu().find((m) => m.label === 'English')?.command?.({} as never);
    expect(lang.setLang).toHaveBeenCalledWith('en');
  });

  it('profileMenu : navigation vers /profile et logout', () => {
    const comp = create();
    const menu = comp.profileMenu();
    menu[0].command?.({} as never); // profil
    expect(router.navigate).toHaveBeenCalledWith(['/profile']);
    menu[2].command?.({} as never); // logout (après le séparateur)
    expect(auth.logout).toHaveBeenCalled();
  });

  it('toggle inverse l’état replié', () => {
    const comp = create();
    expect(comp.collapsed()).toBe(false);
    comp.toggle();
    expect(comp.collapsed()).toBe(true);
  });
});
