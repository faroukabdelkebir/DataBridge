"""
Django settings for SEO7 AI Agents.
Variables d'environnement dans .env 
"""


import os
from pathlib import Path
from dotenv import load_dotenv
 
load_dotenv(override=True) 
 
BASE_DIR = Path(__file__).resolve().parent
SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "")
DEBUG = os.getenv("DJANGO_DEBUG", "True").lower() == "true"
ALLOWED_HOSTS = ["*"]
 
INSTALLED_APPS = [
    "django.contrib.contenttypes",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "agent_anomaly_quality",
    "agent_scheduler",
]
 
MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.common.CommonMiddleware",
]
 
 
ROOT_URLCONF = "urls"
WSGI_APPLICATION = "wsgi.application"
 
DATABASES = {}  # Pas de DB Django, on utilise pyodbc directement
 
REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_PARSER_CLASSES": ["rest_framework.parsers.JSONParser"],
    "UNAUTHENTICATED_USER": None,
    # OAuth2 Resource Server : tout est protege par defaut (deny by default).
    "DEFAULT_AUTHENTICATION_CLASSES": ["core.oauth2_auth.OAuth2JWTAuthentication"],
    "DEFAULT_PERMISSION_CLASSES": ["core.oauth2_auth.HasScope"],
}
 
# =============================================
# OAuth2 Resource Server (validation des JWT AuthMS)
# =============================================
AUTHMS_URL = os.getenv("AUTHMS_URL", "http://localhost:8090")
OAUTH2_JWKS_URL = f"{AUTHMS_URL}/oauth2/jwks"
OAUTH2_ISSUER = os.getenv("OAUTH2_ISSUER", AUTHMS_URL)
# Secret HMAC partagé avec AuthMS (tokens utilisateur HS256).
# MÊME valeur que JWT_SECRET côté AuthMS (Base64).

# Scopes dérivés du rôle utilisateur
ROLE_SCOPES = {
    "ADMIN":   {"ai:read", "ai:execute"},
    "MONITOR": {"ai:read", "ai:execute"},
    "VISITOR": {"ai:read"},
}
 
STATIC_URL = "/static/"
 
# =============================================
# SQL Server (BI destination)
# =============================================
BI_HOST = os.getenv("BI_HOST", "127.0.0.1")
BI_PORT = os.getenv("BI_PORT", "1433")
BI_DB = os.getenv("BI_DB", "T24_TEST")
BI_USER = os.getenv("BI_USER", "T24")
BI_PASS = os.getenv("BI_PASS", "")
 
# =============================================
# Oracle T24 (source)
# =============================================
ORACLE_HOST = os.getenv("ORACLE_HOST", "127.0.0.1")
ORACLE_PORT = os.getenv("ORACLE_PORT", "1521")
ORACLE_SID = os.getenv("ORACLE_SID", "XE")
ORACLE_USER = os.getenv("ORACLE_USER", "T24_USER")
ORACLE_PASS = os.getenv("ORACLE_PASS", "")
 
# =============================================
# MySQL (stockage des rapports/résultats des agents)
# =============================================
MYSQL_HOST = os.getenv("MYSQL_HOST", "127.0.0.1")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_DB = os.getenv("MYSQL_DB", "ai_agents")
MYSQL_USER = os.getenv("MYSQL_USER", "wevioo")
MYSQL_PASS = os.getenv("MYSQL_PASS", "")

# =============================================
# Ollama
# =============================================
OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "Qwen2.5:7b")
 
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
 