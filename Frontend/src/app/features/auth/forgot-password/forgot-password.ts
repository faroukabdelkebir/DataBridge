import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  imports: [
    ReactiveFormsModule, RouterLink, TranslatePipe,
    InputTextModule, ButtonModule, MessageModule,
  ],
  templateUrl: './forgot-password.html',
  styleUrl: '../login/auth.scss',
})
export class ForgotPassword {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly sent = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.auth.forgotPassword(this.form.getRawValue()).subscribe({
      // On affiche toujours un message générique (ne pas révéler si l'email existe).
      next: () => {
        this.loading.set(false);
        this.sent.set(true);
      },
      error: () => {
        this.error.set('auth.forgot.error');
        this.loading.set(false);
      },
    });
  }
}
