import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { ThemeService } from './theme.service';

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('app-dark');
  });

  afterEach(() => vi.restoreAllMocks());

  function create() {
    TestBed.configureTestingModule({ providers: [ThemeService] });
    const service = TestBed.inject(ThemeService);
    TestBed.tick(); // exécute l'effect de synchronisation DOM/localStorage
    return service;
  }

  it('utilise le choix sauvegardé (dark)', () => {
    localStorage.setItem('bi_theme', 'dark');
    const service = create();
    expect(service.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
  });

  it('utilise le choix sauvegardé (light)', () => {
    localStorage.setItem('bi_theme', 'light');
    const service = create();
    expect(service.darkMode()).toBe(false);
    expect(document.documentElement.classList.contains('app-dark')).toBe(false);
  });

  it('retombe sur la préférence système sans choix sauvegardé', () => {
    // jsdom n'implémente pas matchMedia : on le fournit pour ce test.
    (window as unknown as { matchMedia: unknown }).matchMedia = () => ({ matches: true });
    const service = create();
    expect(service.darkMode()).toBe(true);
    delete (window as unknown as { matchMedia?: unknown }).matchMedia;
  });

  it('toggle inverse le thème, applique la classe et persiste', () => {
    localStorage.setItem('bi_theme', 'light');
    const service = create();

    service.toggle();
    TestBed.tick();

    expect(service.darkMode()).toBe(true);
    expect(document.documentElement.classList.contains('app-dark')).toBe(true);
    expect(localStorage.getItem('bi_theme')).toBe('dark');

    service.toggle();
    TestBed.tick();

    expect(service.darkMode()).toBe(false);
    expect(localStorage.getItem('bi_theme')).toBe('light');
  });
});
