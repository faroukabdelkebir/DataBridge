import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ForgotPassword } from './forgot-password';
import { AuthService } from '../../../core/services/auth.service';

describe('ForgotPassword (composant)', () => {
  let auth: { forgotPassword: ReturnType<typeof vi.fn> };

  function create() {
    TestBed.configureTestingModule({
      providers: [ForgotPassword, { provide: AuthService, useValue: auth }],
    });
    return TestBed.inject(ForgotPassword);
  }

  beforeEach(() => {
    auth = { forgotPassword: vi.fn().mockReturnValue(of({ message: 'ok' })) };
  });

  it('rejette un email invalide', () => {
    const comp = create();
    comp.form.setValue({ email: 'not-an-email' });
    expect(comp.form.invalid).toBe(true);
  });

  it('ne soumet pas un formulaire invalide', () => {
    const comp = create();
    comp.submit();
    expect(auth.forgotPassword).not.toHaveBeenCalled();
  });

  it('sur succès : passe l’état "sent" à vrai', () => {
    const comp = create();
    comp.form.setValue({ email: 'a@b.com' });

    comp.submit();

    expect(auth.forgotPassword).toHaveBeenCalledWith({ email: 'a@b.com' });
    expect(comp.sent()).toBe(true);
    expect(comp.loading()).toBe(false);
    expect(comp.error()).toBeNull();
  });

  it('sur erreur : message générique, pas d’état "sent"', () => {
    auth.forgotPassword.mockReturnValue(throwError(() => ({ status: 500 })));
    const comp = create();
    comp.form.setValue({ email: 'a@b.com' });

    comp.submit();

    expect(comp.error()).toBe('auth.forgot.error');
    expect(comp.sent()).toBe(false);
    expect(comp.loading()).toBe(false);
  });
});
