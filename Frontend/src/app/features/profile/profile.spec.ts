import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { MessageService } from 'primeng/api';

import { Profile } from './profile';
import { AuthService } from '../../core/services/auth.service';
import { AuthAuditLog } from '../../core/models/auth.model';

const VALID_PWD = 'Aa1@abcd';

describe('Profile (composant)', () => {
  let auth: {
    user: unknown;
    hasRole: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    logoutAll: ReturnType<typeof vi.fn>;
    getAuditLogs: ReturnType<typeof vi.fn>;
  };
  let toast: { add: ReturnType<typeof vi.fn> };

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Profile,
        { provide: AuthService, useValue: auth },
        { provide: MessageService, useValue: toast },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
      ],
    });
    return TestBed.inject(Profile);
  }

  beforeEach(() => {
    auth = {
      user: signal({ username: 'alice', role: 'ADMIN' }),
      hasRole: vi.fn().mockReturnValue(true),
      changePassword: vi.fn().mockReturnValue(of({ message: 'ok' })),
      logoutAll: vi.fn().mockReturnValue(of({ message: 'ok' })),
      getAuditLogs: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })),
    };
    toast = { add: vi.fn() };
  });

  describe('formulaire de mot de passe', () => {
    it('invalide si le nouveau mot de passe ne respecte pas la politique', () => {
      const comp = create();
      comp.pwdForm.setValue({ currentPassword: 'x', newPassword: 'weak', confirmPassword: 'weak' });
      expect(comp.pwdForm.invalid).toBe(true);
    });

    it('invalide si la confirmation diffère', () => {
      const comp = create();
      comp.pwdForm.setValue({ currentPassword: 'x', newPassword: VALID_PWD, confirmPassword: VALID_PWD + '1' });
      expect(comp.pwdForm.errors).toEqual({ mismatch: true });
    });

    it('ne soumet pas un formulaire invalide', () => {
      const comp = create();
      comp.changePassword();
      expect(auth.changePassword).not.toHaveBeenCalled();
    });

    it('sur succès : réinitialise le formulaire et notifie', () => {
      const comp = create();
      comp.pwdForm.setValue({ currentPassword: 'old', newPassword: VALID_PWD, confirmPassword: VALID_PWD });

      comp.changePassword();

      expect(auth.changePassword).toHaveBeenCalled();
      expect(comp.saving()).toBe(false);
      expect(comp.pwdForm.getRawValue().newPassword).toBe('');
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('sur erreur : notifie avec le détail du backend', () => {
      auth.changePassword.mockReturnValue(throwError(() => ({ error: { message: 'wrong current' } })));
      const comp = create();
      comp.pwdForm.setValue({ currentPassword: 'old', newPassword: VALID_PWD, confirmPassword: VALID_PWD });

      comp.changePassword();

      expect(comp.saving()).toBe(false);
      expect(toast.add).toHaveBeenCalledWith(
        expect.objectContaining({ severity: 'error', detail: 'wrong current' }),
      );
    });
  });

  describe('loadLogs', () => {
    it('calcule la page depuis l’événement lazy de la table', () => {
      const comp = create();
      comp.loadLogs({ first: 40, rows: 20 } as never);
      expect(auth.getAuditLogs).toHaveBeenCalledWith(
        { username: undefined, eventType: null },
        2, // 40 / 20
        20,
      );
    });

    it('remplit logs et total sur succès', () => {
      auth.getAuditLogs.mockReturnValue(of({ content: [{ id: '1' }], totalElements: 5 }));
      const comp = create();
      comp.loadLogs();
      expect(comp.logs()).toHaveLength(1);
      expect(comp.totalLogs()).toBe(5);
      expect(comp.logsLoading()).toBe(false);
    });

    it('passe les filtres username/eventType', () => {
      const comp = create();
      comp.filterUsername = 'bob';
      comp.filterEvent = 'LOGIN_FAILURE';
      comp.loadLogs();
      expect(auth.getAuditLogs).toHaveBeenCalledWith(
        { username: 'bob', eventType: 'LOGIN_FAILURE' },
        0,
        20,
      );
    });
  });

  describe('eventSeverity', () => {
    const log = (o: Partial<AuthAuditLog>): AuthAuditLog =>
      ({ success: true, eventType: 'LOGIN_SUCCESS', ...o } as AuthAuditLog);

    it('échec → danger quel que soit le type', () => {
      const comp = create();
      expect(comp.eventSeverity(log({ success: false, eventType: 'LOGIN_SUCCESS' }))).toBe('danger');
    });

    it('mappe les types en cas de succès', () => {
      const comp = create();
      expect(comp.eventSeverity(log({ eventType: 'LOGIN_SUCCESS' }))).toBe('success');
      expect(comp.eventSeverity(log({ eventType: 'LOGOUT' }))).toBe('info');
      expect(comp.eventSeverity(log({ eventType: 'PASSWORD_CHANGE' }))).toBe('warn');
      expect(comp.eventSeverity(log({ eventType: 'PASSWORD_RESET_SUCCESS' }))).toBe('warn');
      expect(comp.eventSeverity(log({ eventType: 'TOKEN_REFRESH' }))).toBe('info');
    });
  });

  it('logoutAll délègue au service', () => {
    const comp = create();
    comp.logoutAll();
    expect(auth.logoutAll).toHaveBeenCalled();
  });
});
