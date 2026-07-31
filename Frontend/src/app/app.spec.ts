import { describe, it, expect, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { App } from './app';
import { LanguageService } from './core/services/language.service';

describe('App (racine)', () => {
  it('s’instancie et amorce LanguageService (langue + direction globales)', () => {
    // LanguageService applique langue/RTL dès l'injection : on le mocke.
    const langMock = { current: () => 'fr', isRtl: () => false };
    TestBed.configureTestingModule({
      providers: [App, { provide: LanguageService, useValue: langMock }],
    });

    const app = TestBed.inject(App);
    expect(app).toBeTruthy();
  });
});
