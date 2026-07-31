import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { PrimeNG } from 'primeng/config';

import { LanguageService } from './language.service';

describe('LanguageService', () => {
  let translate: {
    addLangs: ReturnType<typeof vi.fn>;
    use: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    getBrowserLang: ReturnType<typeof vi.fn>;
  };
  let primeng: { setTranslation: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('dir');
    document.documentElement.removeAttribute('lang');
    translate = {
      addLangs: vi.fn(),
      use: vi.fn(),
      get: vi.fn().mockReturnValue(of({ accept: 'Oui' })),
      getBrowserLang: vi.fn().mockReturnValue('en'),
    };
    primeng = { setTranslation: vi.fn() };
  });

  function create() {
    TestBed.configureTestingModule({
      providers: [
        LanguageService,
        { provide: TranslateService, useValue: translate },
        { provide: PrimeNG, useValue: primeng },
      ],
    });
    const service = TestBed.inject(LanguageService);
    TestBed.tick(); // exécute l'effect
    return service;
  }

  it('enregistre les langues supportées au démarrage', () => {
    create();
    expect(translate.addLangs).toHaveBeenCalledWith(['fr', 'en', 'ar']);
  });

  it('restaure la langue sauvegardée et l’applique', () => {
    localStorage.setItem('bi_lang', 'en');
    const service = create();

    expect(service.current()).toBe('en');
    expect(translate.use).toHaveBeenCalledWith('en');
    expect(document.documentElement.getAttribute('lang')).toBe('en');
    expect(document.documentElement.getAttribute('dir')).toBe('ltr');
    expect(primeng.setTranslation).toHaveBeenCalledWith({ accept: 'Oui' });
  });

  it('utilise la langue du navigateur si aucune sauvegarde', () => {
    translate.getBrowserLang.mockReturnValue('ar');
    const service = create();
    expect(service.current()).toBe('ar');
  });

  it('retombe sur "fr" si la langue du navigateur n’est pas supportée', () => {
    translate.getBrowserLang.mockReturnValue('de');
    const service = create();
    expect(service.current()).toBe('fr');
  });

  it('applique la direction RTL pour l’arabe', () => {
    const service = create();
    service.setLang('ar');
    TestBed.tick();

    expect(service.isRtl()).toBe(true);
    expect(document.documentElement.getAttribute('dir')).toBe('rtl');
    expect(localStorage.getItem('bi_lang')).toBe('ar');
  });

  it('isRtl est faux pour les langues LTR', () => {
    const service = create();
    service.setLang('fr');
    TestBed.tick();
    expect(service.isRtl()).toBe(false);
  });
});
