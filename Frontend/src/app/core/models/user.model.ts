// ─────────────────────────────────────────────────────────────
// UserMS — miroir des DTOs Java
// ─────────────────────────────────────────────────────────────

export type Role = 'ADMIN' | 'MONITOR' | 'VISITOR';

/** UserResponse — GET /users/{id}, POST /users, PATCH /users/{id}. */
export interface UserResponse {
  id: string;              // UUID
  username: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  role: Role;
  active: boolean;
  externalId: string | null;
  createdAt: string;       // Instant ISO-8601
  updatedAt: string;
  lastLoginAt: string | null;
}

/** UserSummary — GET /users (liste paginée). */
export interface UserSummary {
  id: string;
  username: string;
  email: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

/**
 * CreateUserRequest — POST /users.
 * Contraintes backend : username 3-60 [a-zA-Z0-9._-],
 * email max 150, password selon PASSWORD_PATTERN (auth.model),
 * firstName/lastName max 80.
 */
export interface CreateUserRequest {
  username: string;
  email: string;
  password: string;
  firstName?: string;
  lastName?: string;
  role: Role;
}

/** UpdateUserRequest — PATCH /users/{id}. Tous les champs optionnels. */
export interface UpdateUserRequest {
  email?: string;
  password?: string;
  firstName?: string;
  lastName?: string;
  role?: Role;
  active?: boolean;
}

/** Filtres de recherche — GET /users?search=&role=&active= */
export interface UserSearchFilters {
  search?: string;
  role?: Role;
  active?: boolean;
}