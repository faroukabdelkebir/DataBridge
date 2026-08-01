"""
Tests unitaires du Resource Server OAuth2 (core/oauth2_auth.py).

Les JWT sont de VRAIS tokens RS256 signes avec une paire de cles generee pour la
suite : on teste la validation cryptographique reelle, seul le endpoint JWKS
d'AuthMS est double.
"""

import datetime

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from django.conf import settings
from rest_framework.exceptions import AuthenticationFailed
from rest_framework.test import APIRequestFactory

from core.oauth2_auth import HasScope, OAuth2JWTAuthentication, OAuth2Principal


@pytest.fixture(scope="module")
def rsa_keys():
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private, private.public_key()


@pytest.fixture
def make_token(rsa_keys):
    private, _ = rsa_keys

    def _make(**overrides):
        now = datetime.datetime.now(datetime.timezone.utc)
        claims = {
            "iss": settings.OAUTH2_ISSUER,
            "sub": "svc-extractor",
            "client_id": "extractor-ms",
            "scope": "ai:read ai:execute",
            "iat": now,
            "exp": now + datetime.timedelta(minutes=5),
        }
        claims.update(overrides)
        return jwt.encode(claims, private, algorithm="RS256")

    return _make


@pytest.fixture
def stub_jwks(monkeypatch, rsa_keys):
    """Remplace le client JWKS : la cle publique est servie sans appel reseau."""
    _, public = rsa_keys

    class _SigningKey:
        key = public

    class _Client:
        def get_signing_key_from_jwt(self, token):
            return _SigningKey()

    monkeypatch.setattr("core.oauth2_auth._get_jwk_client", lambda: _Client())


def _request(authorization=None):
    factory = APIRequestFactory()
    headers = {"HTTP_AUTHORIZATION": authorization} if authorization else {}
    return factory.get("/api/reports/", **headers)


# ╔══════════════════════════════════════════════════════════════════╗
# ║  OAuth2Principal                                                 ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestOAuth2Principal:

    def test_scope_en_chaine_est_decoupe(self):
        principal = OAuth2Principal({"scope": "ai:read ai:execute"})
        assert principal.scopes == {"ai:read", "ai:execute"}

    def test_scope_en_liste_est_accepte(self):
        principal = OAuth2Principal({"scope": ["ai:read"]})
        assert principal.scopes == {"ai:read"}

    def test_scope_absent_donne_un_ensemble_vide(self):
        assert OAuth2Principal({}).scopes == set()

    def test_client_id_prioritaire_sur_sub(self):
        assert OAuth2Principal({"client_id": "ms", "sub": "u1"}).client_id == "ms"

    def test_repli_sur_sub_puis_unknown(self):
        assert OAuth2Principal({"sub": "u1"}).client_id == "u1"
        assert OAuth2Principal({}).client_id == "unknown"

    def test_principal_est_toujours_authentifie(self):
        assert OAuth2Principal({}).is_authenticated is True

    def test_str_expose_le_client_id(self):
        assert str(OAuth2Principal({"client_id": "ms"})) == "ms"


# ╔══════════════════════════════════════════════════════════════════╗
# ║  OAuth2JWTAuthentication                                         ║
# ╚══════════════════════════════════════════════════════════════════╝

class TestAuthentication:

    def test_absence_de_header_laisse_passer_aux_permissions(self):
        assert OAuth2JWTAuthentication().authenticate(_request()) is None

    def test_schema_non_bearer_est_ignore(self):
        assert OAuth2JWTAuthentication().authenticate(_request("Basic abc")) is None

    def test_token_valide_produit_un_principal(self, stub_jwks, make_token):
        principal, claims = OAuth2JWTAuthentication().authenticate(
            _request(f"Bearer {make_token()}")
        )
        assert principal.client_id == "extractor-ms"
        assert principal.scopes == {"ai:read", "ai:execute"}
        assert claims["iss"] == settings.OAUTH2_ISSUER

    def test_token_expire_est_refuse(self, stub_jwks, make_token):
        past = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(hours=1)
        token = make_token(exp=past, iat=past - datetime.timedelta(minutes=5))
        with pytest.raises(AuthenticationFailed, match="expiré"):
            OAuth2JWTAuthentication().authenticate(_request(f"Bearer {token}"))

    def test_issuer_inattendu_est_refuse(self, stub_jwks, make_token):
        token = make_token(iss="http://attaquant.invalid")
        with pytest.raises(AuthenticationFailed, match="Issuer"):
            OAuth2JWTAuthentication().authenticate(_request(f"Bearer {token}"))

    def test_signature_invalide_est_refusee(self, stub_jwks, make_token):
        token = make_token()[:-6] + "AAAAAA"
        with pytest.raises(AuthenticationFailed, match="Token invalide"):
            OAuth2JWTAuthentication().authenticate(_request(f"Bearer {token}"))

    def test_jeton_illisible_est_refuse(self, stub_jwks):
        with pytest.raises(AuthenticationFailed, match="Token invalide"):
            OAuth2JWTAuthentication().authenticate(_request("Bearer pas-un-jwt"))

    def test_algorithme_hs256_est_rejete(self, stub_jwks):
        # Regression : seul RS256 est accepte (parade a l'attaque "alg confusion").
        token = jwt.encode({"iss": settings.OAUTH2_ISSUER, "scope": "ai:execute"},
                           "secret-hmac-partage-de-32-octets-minimum", algorithm="HS256")
        with pytest.raises(AuthenticationFailed):
            OAuth2JWTAuthentication().authenticate(_request(f"Bearer {token}"))

    def test_challenge_renvoie_bearer(self):
        header = OAuth2JWTAuthentication().authenticate_header(_request())
        assert header == 'Bearer realm="ai-agents"'


# ╔══════════════════════════════════════════════════════════════════╗
# ║  HasScope                                                        ║
# ╚══════════════════════════════════════════════════════════════════╝

class _View:
    def __init__(self, required_scopes=None):
        if required_scopes is not None:
            self.required_scopes = required_scopes


class _Req:
    def __init__(self, user, method="GET"):
        self.user = user
        self.method = method


class TestHasScope:

    def test_refus_si_non_authentifie(self):
        assert HasScope().has_permission(_Req(None), _View(["ai:read"])) is False

    def test_refus_si_user_non_authentifie(self):
        class _Anon:
            is_authenticated = False

        assert HasScope().has_permission(_Req(_Anon()), _View(["ai:read"])) is False

    def test_vue_sans_scope_declare_accepte_tout_authentifie(self, api_principal):
        assert HasScope().has_permission(_Req(api_principal), _View()) is True

    def test_liste_de_scopes_couverte(self, api_principal):
        assert HasScope().has_permission(_Req(api_principal), _View(["ai:read"])) is True

    def test_scope_manquant_est_refuse(self):
        visitor = OAuth2Principal({"scope": "ai:read"})
        assert HasScope().has_permission(_Req(visitor), _View(["ai:execute"])) is False

    def test_scopes_par_methode_http(self):
        visitor = OAuth2Principal({"scope": "ai:read"})
        view = _View({"GET": ["ai:read"], "POST": ["ai:execute"]})
        assert HasScope().has_permission(_Req(visitor, "GET"), view) is True
        assert HasScope().has_permission(_Req(visitor, "POST"), view) is False

    def test_methode_absente_du_mapping_est_autorisee(self, api_principal):
        view = _View({"POST": ["ai:execute"]})
        assert HasScope().has_permission(_Req(api_principal, "DELETE"), view) is True

    def test_liste_de_scopes_vide_est_autorisee(self, api_principal):
        assert HasScope().has_permission(_Req(api_principal), _View([])) is True

    def test_un_seul_scope_suffit(self):
        principal = OAuth2Principal({"scope": "ai:read"})
        view = _View(["ai:execute", "ai:read"])
        assert HasScope().has_permission(_Req(principal), view) is True
