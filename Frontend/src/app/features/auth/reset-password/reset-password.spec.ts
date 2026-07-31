import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';

import { ResetPassword } from './reset-password';
import { AuthService } from '../../../core/services/auth.service';

describe('ResetPassword (composant)', () => {
  let auth: { resetPassword: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };
  let toast: { add: ReturnType<typeof vi.fn> };
  let token: string | null;

  const VALID_PWD = 'Aa1@abcd'; // respecte PASSWORD_PATTERN

  function create() {
    TestBed.configureTestingModule({
      providers: [
        ResetPassword,
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: MessageService, useValue: toast },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: { get: () => token } } },
        },
      ],
    });
    return TestBed.inject(ResetPassword);
  }

  beforeEach(() => {
    token = 'reset-token';
    auth = { resetPassword: vi.fn().mockReturnValue(of({ message: 'ok' })) };
    router = { navigate: vi.fn() };
    toast = { add: vi.fn() };
  });

  it('signale un token manquant', () => {
    token = null;
    const comp = create();
    expect(comp.missingToken()).toBe(true);
  });

  it('rejette un mot de passe qui ne respecte pas la politique', () => {
    const comp = create();
    comp.form.setValue({ newPassword: 'weak', confirmPassword: 'weak' });
    expect(comp.form.invalid).toBe(true);
  });

  it('rejette des mots de passe différents (validateur de groupe)', () => {
    const comp = create();
    comp.form.setValue({ newPassword: VALID_PWD, confirmPassword: VALID_PWD + 'x' });
    expect(comp.form.errors).toEqual({ mismatch: true });
  });

  it('accepte un mot de passe valide et confirmé', () => {
    const comp = create();
    comp.form.setValue({ newPassword: VALID_PWD, confirmPassword: VALID_PWD });
    expect(comp.form.valid).toBe(true);
  });

  it('sur succès : toast + redirection vers /login avec le token', () => {
    const comp = create();
    comp.form.setValue({ newPassword: VALID_PWD, confirmPassword: VALID_PWD });

    comp.submit();

    expect(auth.resetPassword).toHaveBeenCalledWith({
      token: 'reset-token',
      newPassword: VALID_PWD,
      confirmPassword: VALID_PWD,
    });
    expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('sur 410 : message de token invalide/expiré', () => {
    auth.resetPassword.mockReturnValue(throwError(() => ({ status: 410 })));
    const comp = create();
    comp.form.setValue({ newPassword: VALID_PWD, confirmPassword: VALID_PWD });

    comp.submit();

    expect(comp.error()).toBe('auth.reset.invalidToken');
  });

  it('sur 500 : message d’erreur serveur', () => {
    auth.resetPassword.mockReturnValue(throwError(() => ({ status: 500 })));
    const comp = create();
    comp.form.setValue({ newPassword: VALID_PWD, confirmPassword: VALID_PWD });

    comp.submit();

    expect(comp.error()).toBe('auth.reset.error');
  });
});
