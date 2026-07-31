import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { ConfirmationService, MessageService } from 'primeng/api';

import { UserService } from '../../../core/services/user.service';
import { Role, UserSummary } from '../../../core/models/user.model';
import { PASSWORD_PATTERN } from '../../../core/models/auth.model';

@Component({
  selector: 'app-users',
  imports: [
    TranslatePipe, ReactiveFormsModule, DatePipe,
    TableModule, TagModule, ButtonModule, DialogModule,
    InputTextModule, PasswordModule, SelectModule, ConfirmDialogModule,
  ],
  providers: [ConfirmationService],
  templateUrl: './users.html',
})
export class Users {
  private userService = inject(UserService);
  private fb = inject(FormBuilder);
  private toast = inject(MessageService);
  private confirm = inject(ConfirmationService);
  private translate = inject(TranslateService);

  readonly roles: Role[] = ['ADMIN', 'MONITOR', 'VISITOR'];

  readonly users = signal<UserSummary[]>([]);
  readonly totalRecords = signal(0);
  readonly loading = signal(true);
  readonly pageSize = 10;

  readonly dialogVisible = signal(false);
  readonly saving = signal(false);
  readonly editingId = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(60)]],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.minLength(8), Validators.pattern(PASSWORD_PATTERN)]],
    firstName: [''],
    lastName: [''],
    role: ['MONITOR' as Role, Validators.required],
  });

  load(event?: TableLazyLoadEvent): void {
    this.loading.set(true);
    const page = event ? (event.first ?? 0) / (event.rows ?? this.pageSize) : 0;
    this.userService.search({}, page, this.pageSize).subscribe({
      next: (res) => {
        this.users.set(res.content);
        this.totalRecords.set(res.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  openCreate(): void {
    this.editingId.set(null);
    this.form.reset({ role: 'MONITOR' });
    this.form.controls.username.enable();
    this.form.controls.password.addValidators(Validators.required);
    this.dialogVisible.set(true);
  }

  openEdit(user: UserSummary): void {
    this.editingId.set(user.id);
    this.form.reset({ username: user.username, email: user.email, role: user.role });
    this.form.controls.username.disable();          // username immuable
    this.form.controls.password.removeValidators(Validators.required); // vide = inchangé
    this.dialogVisible.set(true);

    // La liste ne contient pas firstName/lastName : on charge l'utilisateur complet
    this.userService.getById(user.id).subscribe({
      next: (u) => this.form.patchValue({
        email: u.email,
        role: u.role,
        firstName: u.firstName ?? '',
        lastName: u.lastName ?? '',
      }),
    });
  }

  save(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    const v = this.form.getRawValue();
    const id = this.editingId();

    const req = id
      ? this.userService.update(id, {
          email: v.email, role: v.role,
          firstName: v.firstName || undefined,
          lastName: v.lastName || undefined,
          password: v.password || undefined,
        })
      : this.userService.create({
          username: v.username, email: v.email, password: v.password,
          firstName: v.firstName || undefined, lastName: v.lastName || undefined,
          role: v.role,
        });

    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.dialogVisible.set(false);
        this.toast.add({ severity: 'success', summary: this.translate.instant('users.saved') });
        this.load();
      },
      error: (err) => {
        this.saving.set(false);
        const detail = err?.error?.message ?? this.translate.instant('common.error');
        this.toast.add({ severity: 'error', summary: this.translate.instant('common.error'), detail });
      },
    });
  }

  toggleActive(user: UserSummary): void {
    const call = user.active
      ? this.userService.deactivate(user.id)
      : this.userService.activate(user.id);
    call.subscribe({ next: () => this.load() });
  }

  remove(user: UserSummary): void {
    this.confirm.confirm({
      message: this.translate.instant('users.confirmDelete', { name: user.username }),
      header: this.translate.instant('common.confirm'),
      icon: 'pi pi-exclamation-triangle',
      acceptButtonProps: { severity: 'danger' },
      accept: () => this.userService.delete(user.id).subscribe({ next: () => this.load() }),
    });
  }

  roleSeverity(role: Role): 'danger' | 'info' | 'secondary' {
    switch (role) {
      case 'ADMIN': return 'danger';
      case 'MONITOR': return 'info';
      default: return 'secondary';
    }
  }
}