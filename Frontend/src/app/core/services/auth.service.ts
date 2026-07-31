import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpContext, HttpContextToken, HttpParams } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, map, of, shareReplay, tap, throwError } from 'rxjs';

import { API } from '../config/api.config';
import {
  AuthEventType,
  AuthAuditLog,
  AuthResponse,
  ChangePasswordRequest,
  CurrentUser,
  ForgotPasswordRequest,
  LoginRequest,
  ResetPasswordRequest,
  SessionUser,
} from '../models/auth.model';
import { MessageResponse, Page } from '../models/api.model';
import { Role } from '../models/user.model';

// Clés localStorage
const ACCESS_TOKEN_KEY  = 'bi_access_token';
const REFRESH_TOKEN_KEY = 'bi_refresh_token';
const EXPIRES_AT_KEY    = 'bi_expires_at';
const USER_KEY          = 'bi_user';

/**
 * Contexte HTTP : marque une requête comme "ne pas tenter de refresh sur 401".
 * Utilisé par l'interceptor pour /login, /refresh, etc.
 */
export const SKIP_AUTH_REFRESH = new HttpContextToken<boolean>(() => false);

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private expiryTimer: ReturnType<typeof setInterval> | null = null;

  // ── État réactif (signals) ─────────────────────────────────
  private readonly _user = signal<SessionUser | null>(this.restoreUser());
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => this._user() !== null);
  readonly role = computed(() => this._user()?.role ?? null);

  /** Refresh en cours partagé : évite N appels /refresh simultanés sur N 401. */
  private refreshInFlight$: Observable<AuthResponse> | null = null;

  // ── Accès tokens ───────────────────────────────────────────

  get accessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  }

  /** true si le token expire dans moins de `marginSec` secondes. */
  isAccessTokenExpired(marginSec = 15): boolean {
    const raw = localStorage.getItem(EXPIRES_AT_KEY);
    if (!raw) return true;
    return Date.now() >= Number(raw) - marginSec * 1000;
  }

  hasRole(...roles: Role[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r as Role);
  }

  // ── Login / Logout ─────────────────────────────────────────

  login(payload: LoginRequest): Observable<SessionUser> {
    return this.http
      .post<AuthResponse>(API.auth.login, payload, { context: skipRefresh() })
      .pipe(
        tap((res) => this.persistSession(res)),
        map(() => this._user()!),
      );
  }

  /**
   * Déconnexion : révoque le refresh token côté serveur (best effort),
   * puis nettoie la session locale dans tous les cas.
   */
  logout(): void {
    const rt = this.refreshToken;
    if (rt) {
      this.http
        .post<MessageResponse>(API.auth.logout, { refreshToken: rt }, { context: skipRefresh() })
        .pipe(catchError(() => of(null)))
        .subscribe();
    }
    this.clearSession();
    this.router.navigate(['/login']);
  }

  /** Révoque TOUTES les sessions de l'utilisateur (tous appareils). */
  logoutAll(): Observable<MessageResponse> {
    return this.http
      .post<MessageResponse>(API.auth.logoutAll, {})
      .pipe(finalize(() => {
        this.clearSession();
        this.router.navigate(['/login']);
      }));
  }

  // ── Refresh (rotation de token) ────────────────────────────

  /**
   * Rafraîchit la session. Les appels concurrents partagent
   * la même requête HTTP (shareReplay).
   */
  refreshSession(): Observable<AuthResponse> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }

    const rt = this.refreshToken;
    if (!rt) {
      return throwError(() => new Error('NO_REFRESH_TOKEN'));
    }

    this.refreshInFlight$ = this.http
      .post<AuthResponse>(API.auth.refresh, { refreshToken: rt }, { context: skipRefresh() })
      .pipe(
        tap((res) => this.persistSession(res)),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay(1),
      );

    return this.refreshInFlight$;
  }

  /** Vérifie toutes les 30 s ; rafraîchit 60 s avant expiration. */
  private startSessionWatch(): void {
    this.stopSessionWatch();
    this.expiryTimer = setInterval(() => {
      if (!this._user()) return;
      if (!this.isAccessTokenExpired(60)) return;

      if (this.refreshToken) {
        this.refreshSession().subscribe({ error: () => this.expireSession() });
      } else {
        this.expireSession();
      }
    }, 30_000);
  }

  private stopSessionWatch(): void {
    if (this.expiryTimer) {
      clearInterval(this.expiryTimer);
      this.expiryTimer = null;
    }
  }

  /** Session terminée côté serveur : purge + retour login. */
  private expireSession(): void {
    this.clearSession();
    this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  // ── Session au démarrage ───────────────────────────────────

  
  initSession(): Observable<SessionUser | null> {
    if (!this.accessToken && !this.refreshToken) {
      return of(null);
    }
    return this.http.get<CurrentUser>(API.auth.me).pipe(
      map((me) => this.setUserFromMe(me)),
      catchError((err) => {
        if (err?.status === 401 || err?.status === 403) {
          this.clearSession();
          return of(null);
        }
        // Erreur réseau / 5xx : AuthMS indisponible → on garde la
        // session locale, le reste de la plateforme reste utilisable
        return of(this.restoreUser());
      }),
    );
  }

  /** Recharge le profil courant depuis /me. */
  loadCurrentUser(): Observable<SessionUser> {
    return this.http.get<CurrentUser>(API.auth.me).pipe(
      map((me) => this.setUserFromMe(me)),
    );
  }

  // ── Gestion du mot de passe ────────────────────────────────

  forgotPassword(payload: ForgotPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(API.auth.forgotPassword, payload, {
      context: skipRefresh(),
    });
  }

  resetPassword(payload: ResetPasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(API.auth.resetPassword, payload, {
      context: skipRefresh(),
    });
  }

  changePassword(payload: ChangePasswordRequest): Observable<MessageResponse> {
    return this.http.post<MessageResponse>(API.auth.changePassword, payload);
  }

  // ── Helpers privés ─────────────────────────────────────────

  private persistSession(res: AuthResponse): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, res.accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
    localStorage.setItem(EXPIRES_AT_KEY, String(Date.now() + res.expiresIn * 1000));

    const user: SessionUser = {
      id: res.userId,
      username: res.username,
      email: res.email,
      role: res.role,
      fullName: res.username,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
    this.startSessionWatch();
  }

  private setUserFromMe(me: CurrentUser): SessionUser {
    const user: SessionUser = {
      id: me.id,
      username: me.username,
      email: me.email,
      role: me.role,
      fullName: me.username,
    };
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this._user.set(user);
    this.startSessionWatch();
    return user;
  }

  clearSession(): void {
    this.stopSessionWatch();
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(EXPIRES_AT_KEY);
    localStorage.removeItem(USER_KEY);
    this._user.set(null);
  }

  private restoreUser(): SessionUser | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? (JSON.parse(raw) as SessionUser) : null;
    } catch {
      return null;
    }
  }
  getAuditLogs(
    filters: { username?: string; eventType?: AuthEventType | null },
    page = 0,
    size = 20,
  ): Observable<Page<AuthAuditLog>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'createdAt,desc');
    if (filters.username) params = params.set('username', filters.username);
    if (filters.eventType) params = params.set('eventType', filters.eventType);
    return this.http.get<Page<AuthAuditLog>>(API.auth.audit, { params });
  }
}

/** Raccourci : contexte HTTP marquant la requête hors refresh 401. */
export function skipRefresh(): HttpContext {
  return new HttpContext().set(SKIP_AUTH_REFRESH, true);
}

