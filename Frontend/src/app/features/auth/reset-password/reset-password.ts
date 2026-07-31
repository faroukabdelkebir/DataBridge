import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule,
  ValidationErrors, Validators,
} from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';

import { AuthService } from '../../../core/services/auth.service';
import { PASSWORD_PATTERN } from '../../../core/models/auth.model';

@Component({
  selector: 'app-reset-password',
  imports: [
    ReactiveFormsModule, RouterLink, TranslatePipe,
    PasswordModule, ButtonModule, MessageModule,
  ],
  templateUrl: './reset-password.html',
  styleUrl: '../login/auth.scss',
})
export class ResetPassword {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);

  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly missingToken = signal(!this.token);

  readonly form = this.fb.nonNullable.group(
    {
      newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: [matchPasswords] },
  );

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    const { newPassword, confirmPassword } = this.form.getRawValue();
    this.auth.resetPassword({ token: this.token, newPassword, confirmPassword }).subscribe({
      next: () => {
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('auth.reset.success'),
        });
        this.router.navigate(['/login']);
      },
      error: (err) => {
        // 400/410 = token invalide ou expiré ; autre = serveur/réseau
        this.error.set(
          err?.status === 400 || err?.status === 410
            ? 'auth.reset.invalidToken'
            : 'auth.reset.error',
        );
        this.loading.set(false);
      },
    });
  }
}

function matchPasswords(group: AbstractControl): ValidationErrors | null {
  const pwd = group.get('newPassword')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pwd === confirm ? null : { mismatch: true };
}
