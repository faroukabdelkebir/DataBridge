import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService, MessageService } from 'primeng/api';

import { Users } from './users';
import { UserService } from '../../../core/services/user.service';
import { UserSummary } from '../../../core/models/user.model';

const VALID_PWD = 'Aa1@abcd';

function user(o: Partial<UserSummary> = {}): UserSummary {
  return { id: 'u1', username: 'alice', email: 'a@b.com', role: 'MONITOR', active: true, createdAt: '2026-01-01', ...o };
}

describe('Users (composant)', () => {
  let userService: {
    search: ReturnType<typeof vi.fn>;
    getById: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    activate: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let toast: { add: ReturnType<typeof vi.fn> };
  let confirm: { confirm: ReturnType<typeof vi.fn> };

  function create() {
    TestBed.configureTestingModule({
      providers: [
        Users,
        { provide: UserService, useValue: userService },
        { provide: MessageService, useValue: toast },
        { provide: ConfirmationService, useValue: confirm },
        { provide: TranslateService, useValue: { instant: (k: string) => k } },
      ],
    });
    return TestBed.inject(Users);
  }

  beforeEach(() => {
    userService = {
      search: vi.fn().mockReturnValue(of({ content: [], totalElements: 0 })),
      getById: vi.fn().mockReturnValue(of({ email: 'a@b.com', role: 'MONITOR', firstName: 'A', lastName: 'B' })),
      create: vi.fn().mockReturnValue(of({})),
      update: vi.fn().mockReturnValue(of({})),
      activate: vi.fn().mockReturnValue(of(undefined)),
      deactivate: vi.fn().mockReturnValue(of(undefined)),
      delete: vi.fn().mockReturnValue(of(undefined)),
    };
    toast = { add: vi.fn() };
    confirm = { confirm: vi.fn() };
  });

  describe('load', () => {
    it('calcule la page depuis l’événement lazy', () => {
      const comp = create();
      comp.load({ first: 30, rows: 10 } as never);
      expect(userService.search).toHaveBeenCalledWith({}, 3, 10);
    });

    it('remplit users et total sur succès', () => {
      userService.search.mockReturnValue(of({ content: [user()], totalElements: 1 }));
      const comp = create();
      comp.load();
      expect(comp.users()).toHaveLength(1);
      expect(comp.totalRecords()).toBe(1);
      expect(comp.loading()).toBe(false);
    });
  });

  describe('openCreate', () => {
    it('réinitialise le formulaire, active username et rend password requis', () => {
      const comp = create();
      comp.openCreate();
      expect(comp.editingId()).toBeNull();
      expect(comp.dialogVisible()).toBe(true);
      expect(comp.form.controls.username.enabled).toBe(true);
      // password requis en création
      comp.form.controls.password.setValue('');
      expect(comp.form.controls.password.invalid).toBe(true);
    });
  });

  describe('openEdit', () => {
    it('désactive username, retire la contrainte password et pré-remplit', () => {
      const comp = create();
      comp.openEdit(user({ id: 'u9', username: 'bob', email: 'bob@x', role: 'ADMIN' }));

      expect(comp.editingId()).toBe('u9');
      expect(comp.form.controls.username.disabled).toBe(true);
      // password vide autorisé en édition (inchangé)
      expect(comp.form.controls.password.valid).toBe(true);
      expect(userService.getById).toHaveBeenCalledWith('u9');
      // patch depuis getById
      expect(comp.form.getRawValue().firstName).toBe('A');
    });
  });

  describe('save', () => {
    it('ne soumet pas un formulaire invalide', () => {
      const comp = create();
      comp.save();
      expect(userService.create).not.toHaveBeenCalled();
      expect(userService.update).not.toHaveBeenCalled();
    });

    it('crée un nouvel utilisateur quand editingId est null', () => {
      const comp = create();
      comp.openCreate();
      comp.form.setValue({
        username: 'newbie', email: 'n@x.com', password: VALID_PWD,
        firstName: '', lastName: '', role: 'MONITOR',
      });

      comp.save();

      expect(userService.create).toHaveBeenCalledWith(expect.objectContaining({
        username: 'newbie', email: 'n@x.com', password: VALID_PWD, role: 'MONITOR',
      }));
      expect(comp.dialogVisible()).toBe(false);
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
    });

    it('met à jour l’utilisateur en édition', () => {
      const comp = create();
      comp.openEdit(user({ id: 'u9', username: 'bob', email: 'bob@x', role: 'ADMIN' }));
      comp.form.patchValue({ email: 'bob2@x', password: '' });

      comp.save();

      expect(userService.update).toHaveBeenCalledWith('u9', expect.objectContaining({
        email: 'bob2@x',
        password: undefined, // vide → non transmis
      }));
    });

    it('sur erreur : affiche le message du backend', () => {
      userService.create.mockReturnValue(throwError(() => ({ error: { message: 'email exists' } })));
      const comp = create();
      comp.openCreate();
      comp.form.setValue({
        username: 'newbie', email: 'n@x.com', password: VALID_PWD,
        firstName: '', lastName: '', role: 'MONITOR',
      });

      comp.save();

      expect(comp.saving()).toBe(false);
      expect(toast.add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error', detail: 'email exists' }));
    });
  });

  describe('toggleActive', () => {
    it('désactive un utilisateur actif', () => {
      const comp = create();
      comp.toggleActive(user({ active: true }));
      expect(userService.deactivate).toHaveBeenCalledWith('u1');
    });

    it('active un utilisateur inactif', () => {
      const comp = create();
      comp.toggleActive(user({ active: false }));
      expect(userService.activate).toHaveBeenCalledWith('u1');
    });
  });

  describe('remove', () => {
    it('demande confirmation puis supprime à l’acceptation', () => {
      const comp = create();
      comp.remove(user({ id: 'u5' }));

      expect(confirm.confirm).toHaveBeenCalled();
      // exécute le callback accept fourni au ConfirmationService
      const arg = confirm.confirm.mock.calls[0][0];
      arg.accept();
      expect(userService.delete).toHaveBeenCalledWith('u5');
    });
  });

  describe('roleSeverity', () => {
    it('mappe les rôles vers les sévérités de tag', () => {
      const comp = create();
      expect(comp.roleSeverity('ADMIN')).toBe('danger');
      expect(comp.roleSeverity('MONITOR')).toBe('info');
      expect(comp.roleSeverity('VISITOR')).toBe('secondary');
    });
  });
});
