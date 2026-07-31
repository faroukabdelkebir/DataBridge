import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators, AbstractControl, FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TabsModule } from 'primeng/tabs';
import { ButtonModule } from 'primeng/button';
import { PasswordModule } from 'primeng/password';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { AvatarModule } from 'primeng/avatar';
import { MessageService } from 'primeng/api';

import { AuthService } from '../../core/services/auth.service';
import { AuthAuditLog, AuthEventType, PASSWORD_PATTERN } from '../../core/models/auth.model';

@Component({
  selector: 'app-profile',
  imports: [
    TranslatePipe, ReactiveFormsModule, DatePipe, FormsModule,
    TabsModule, ButtonModule, PasswordModule, InputTextModule,
    SelectModule, TableModule, TagModule, AvatarModule,
  ],
  templateUrl: './profile.html',
})
export class Profile {
  private fb = inject(FormBuilder);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);
  readonly auth = inject(AuthService);

  readonly user = this.auth.user;
  readonly isAdmin = this.auth.hasRole('ADMIN');

  // ── Changement de mot de passe ─────────────────────────────
  readonly saving = signal(false);
  readonly pwdForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: (g: AbstractControl) =>
        g.value.newPassword === g.value.confirmPassword ? null : { mismatch: true } },
  );

  changePassword(): void {
    if (this.pwdForm.invalid) { this.pwdForm.markAllAsTouched(); return; }
    this.saving.set(true);
    this.auth.changePassword(this.pwdForm.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.pwdForm.reset();
        this.toast.add({ severity: 'success', summary: this.translate.instant('profile.pwdChanged') });
      },
      error: (err) => {
        this.saving.set(false);
        this.toast.add({
          severity: 'error',
          summary: this.translate.instant('common.error'),
          detail: err?.error?.message ?? '',
        });
      },
    });
  }

  logoutAll(): void {
    this.auth.logoutAll().subscribe();
  }

  // ── Audit (ADMIN) ──────────────────────────────────────────
  readonly eventTypes: AuthEventType[] = [
    'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'TOKEN_REFRESH',
    'TOKEN_REFRESH_FAILURE', 'PASSWORD_RESET_REQUEST',
    'PASSWORD_RESET_SUCCESS', 'PASSWORD_RESET_FAILURE', 'PASSWORD_CHANGE',
  ];
  filterUsername = '';
  filterEvent: AuthEventType | null = null;

  readonly logs = signal<AuthAuditLog[]>([]);
  readonly totalLogs = signal(0);
  readonly logsLoading = signal(false);
  readonly logPageSize = 20;

  loadLogs(event?: TableLazyLoadEvent): void {
    this.logsLoading.set(true);
    const page = event ? (event.first ?? 0) / (event.rows ?? this.logPageSize) : 0;
    this.auth
      .getAuditLogs({ username: this.filterUsername || undefined, eventType: this.filterEvent }, page, this.logPageSize)
      .subscribe({
        next: (res) => {
          this.logs.set(res.content);
          this.totalLogs.set(res.totalElements);
          this.logsLoading.set(false);
        },
        error: () => this.logsLoading.set(false),
      });
  }

  eventSeverity(log: AuthAuditLog): 'success' | 'danger' | 'info' | 'warn' {
    if (!log.success) return 'danger';
    switch (log.eventType) {
      case 'LOGIN_SUCCESS': return 'success';
      case 'LOGOUT': return 'info';
      case 'PASSWORD_CHANGE': case 'PASSWORD_RESET_SUCCESS': return 'warn';
      default: return 'info';
    }
  }
}