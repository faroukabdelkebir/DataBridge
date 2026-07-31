import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  AbstractControl, FormBuilder, ReactiveFormsModule,
  ValidationErrors, Validators,
} from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule, RouterLink, TranslatePipe,
    InputTextModule, PasswordModule, ButtonModule, MessageModule,
  ],
  templateUrl: './register.html',
  styleUrl: '../login/auth.scss',
})
export class Register {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      fullName: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirm: ['', [Validators.required]],
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

    const { fullName, email, password } = this.form.getRawValue();
    this.auth.register({ fullName, email, password }).subscribe({
      next: (user) => {
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('auth.accountCreated'),
          detail: user.fullName,
        });
        this.router.navigate(['/dashboard']);
      },
      error: () => {
        this.error.set('auth.register.error');
        this.loading.set(false);
      },
    });
  }
}

function matchPasswords(group: AbstractControl): ValidationErrors | null {
  const pwd = group.get('password')?.value;
  const confirm = group.get('confirm')?.value;
  return pwd === confirm ? null : { mismatch: true };
}
