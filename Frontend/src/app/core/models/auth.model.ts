// ─────────────────────────────────────────────────────────────
// Requêtes
// ─────────────────────────────────────────────────────────────

/** LoginRequest — ATTENTION : username, pas email. */
export interface LoginRequest {
  username: string;
  password: string;
}

export interface RefreshTokenRequest {
  refreshToken: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

/**
 * Politique mot de passe backend :
 * 8-128 caractères, ≥1 majuscule, ≥1 minuscule, ≥1 chiffre,
 * ≥1 caractère spécial parmi @$!%*?&
 */
export const PASSWORD_PATTERN =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]+$/;

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
}

// ─────────────────────────────────────────────────────────────
// Représentation front de l'utilisateur connecté
// (construite depuis AuthResponse ou /me — pas un DTO backend)
// ─────────────────────────────────────────────────────────────

export interface SessionUser {
  id: string;
  username: string;
  email: string;
  role: string;            // 'ADMIN' | 'MONITOR' | 'VISITOR'
  fullName: string;        // pour l'affichage (username par défaut)
}

// ─────────────────────────────────────────────────────────────
// Réponses
// ─────────────────────────────────────────────────────────────

/** AuthResponse — renvoyée par /login et /refresh. */
export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;       // 'Bearer'
  expiresIn: number;       // secondes
  issuedAt: string;        // Instant ISO-8601
  userId: string;          // UUID
  username: string;
  email: string;
  role: string;            // 'ADMIN' | 'MONITOR' | 'VISITOR'
}

/** CurrentUserResponse — GET /me. */
export interface CurrentUser {
  id: string;              // UUID
  username: string;
  email: string;
  role: string;
}

// ─────────────────────────────────────────────────────────────
// Audit (GET /audit — ADMIN uniquement, paginé)
// ─────────────────────────────────────────────────────────────

export type AuthEventType =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'LOGOUT'
  | 'TOKEN_REFRESH'
  | 'TOKEN_REFRESH_FAILURE'
  | 'PASSWORD_RESET_REQUEST'
  | 'PASSWORD_RESET_SUCCESS'
  | 'PASSWORD_RESET_FAILURE'
  | 'PASSWORD_CHANGE';

export interface AuthAuditLog {
  id: string;              // UUID
  eventType: AuthEventType;
  username: string;
  ipAddress: string;
  userAgent: string;
  success: boolean;
  detail: string | null;
  createdAt: string;       // Instant ISO-8601
}