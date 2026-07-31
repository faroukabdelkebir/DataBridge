"""
OAuth2 Resource Server pour Django REST Framework.

Ce module fait pour Django ce que spring-boot-starter-oauth2-resource-server
fait pour Spring : valider les JWT RSA émis par AuthMS (signature vérifiée
via le endpoint JWKS) et appliquer un contrôle d'accès par scope.

Ce service n'est PAS un client OAuth2 : il n'appelle aucune API protégée
(accès direct pyodbc à SQL Server + Ollama en local).

Usage dans une vue :

    class MaVue(APIView):
        required_scopes = {"GET": ["ai:read"], "POST": ["ai:execute"]}
"""

import jwt
from jwt import PyJWKClient
from django.conf import settings
from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.permissions import BasePermission

# Client JWKS partagé : télécharge les clés publiques d'AuthMS une fois,
# les met en cache, et les re-télécharge si un "kid" inconnu apparaît
# (ex: redémarrage d'AuthMS avec une nouvelle clé).
_jwk_client = None


def _get_jwk_client() -> PyJWKClient:
    global _jwk_client
    if _jwk_client is None:
        _jwk_client = PyJWKClient(settings.OAUTH2_JWKS_URL, cache_keys=True)
    return _jwk_client


class OAuth2Principal:
    """Représente le client machine authentifié (équivalent du principal Spring)."""

    def __init__(self, claims: dict):
        self.claims = claims
        self.client_id = claims.get("client_id") or claims.get("sub", "unknown")
        self.scopes = self._extract_scopes(claims)
        self.is_authenticated = True

    @staticmethod
    def _extract_scopes(claims: dict) -> set:
        """Le claim 'scope' peut être une liste ou une chaîne séparée par des espaces."""
        raw = claims.get("scope", [])
        if isinstance(raw, str):
            return set(raw.split())
        return set(raw)

    def __str__(self):
        return self.client_id


class OAuth2JWTAuthentication(BaseAuthentication):
    """
    Valide le header "Authorization: Bearer <jwt>" :
      1. Récupère la clé publique correspondant au 'kid' du token via JWKS
      2. Vérifie la signature RSA, l'expiration et l'issuer
      3. Expose le client authentifié dans request.user
    """

    def authenticate(self, request):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return None  # Pas de token → DRF passera aux permissions (401)

        token = header[len("Bearer "):].strip()
        try:
            signing_key = _get_jwk_client().get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                issuer=settings.OAUTH2_ISSUER,
                options={"verify_aud": False},
            )
        except jwt.ExpiredSignatureError:
            raise AuthenticationFailed("Token expiré.")
        except jwt.InvalidIssuerError:
            raise AuthenticationFailed("Issuer du token invalide.")
        except jwt.PyJWTError as exc:
            raise AuthenticationFailed(f"Token invalide : {exc}")

        principal = OAuth2Principal(claims)
        return (principal, claims)

    def authenticate_header(self, request):
        # Renvoie 401 (et non 403) quand aucun token n'est fourni.
        return 'Bearer realm="ai-agents"'


class HasScope(BasePermission):
    """
    Contrôle d'accès par scope, équivalent de
    .hasAuthority("SCOPE_xxx") côté Spring.

    La vue déclare ses scopes requis, globalement ou par méthode HTTP :

        required_scopes = ["ai:read"]                            # tous verbes
        required_scopes = {"GET": ["ai:read"], "POST": ["ai:execute"]}
    """

    message = "Scope insuffisant pour cette opération."

    def has_permission(self, request, view):
        user = request.user
        if user is None or not getattr(user, "is_authenticated", False):
            return False

        required = getattr(view, "required_scopes", None)
        if required is None:
            return True  # Authentifié suffit si la vue ne précise rien

        if isinstance(required, dict):
            required = required.get(request.method, [])

        if not required:
            return True

        return any(scope in user.scopes for scope in required)