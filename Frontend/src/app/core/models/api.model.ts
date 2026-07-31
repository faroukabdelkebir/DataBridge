/** Réponse paginée Spring Data (Page<T>). */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;          // index de page courant (0-based)
  size: number;
  numberOfElements: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

/** Paramètres de pagination à envoyer (Pageable). */
export interface PageRequest {
  page?: number;           // 0-based
  size?: number;
  sort?: string;           // ex: 'createdAt,desc'
}

/** MessageResponse (AuthMS) — réponses simples logout, forgot-password, etc. */
export interface MessageResponse {
  message: string;
  timestamp: string;       // Instant ISO-8601
}

/** Corps d'erreur renvoyé par les GlobalExceptionHandler. */
export interface ApiError {
  status?: number;
  error?: string;
  message?: string;
  timestamp?: string;
  path?: string;
  errors?: Record<string, string>;  // erreurs de validation champ -> message
}