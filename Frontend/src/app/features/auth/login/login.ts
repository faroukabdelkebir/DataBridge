import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { MessageService } from 'primeng/api';

import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule, RouterLink, TranslatePipe,
    InputTextModule, PasswordModule, ButtonModule, MessageModule,
  ],
  templateUrl: './login.html',
  styleUrl: './auth.scss',
})
export class Login {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private toast = inject(MessageService);
  private translate = inject(TranslateService);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  constructor() {
    // Session expirée : le guard redirige vers /login?expired=1
    if (this.route.snapshot.queryParamMap.get('expired')) {
      this.error.set('auth.sessionExpired');
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        this.toast.add({
          severity: 'success',
          summary: this.translate.instant('auth.welcome'),
          detail: user.username,
        });
        const returnUrl =
          this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        // 401 = identifiants invalides ; autre = problème serveur/réseau
        this.error.set(
          err?.status === 401 ? 'auth.login.error' : 'auth.login.serverError',
        );
        this.loading.set(false);
      },
    });
  }
}