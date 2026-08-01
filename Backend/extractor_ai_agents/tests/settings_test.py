"""
Settings Django dedies aux tests.

Reprend la configuration de production puis neutralise TOUT ce qui sort du
process : SQL Server, Oracle, MySQL, Ollama et AuthMS pointent sur des hotes
inexistants. Aucun test ne doit atteindre le reseau ; si l'un d'eux oublie un
mock, il echouera bruyamment au lieu de taper sur une vraie base.
"""

from settings import *  # noqa: F401,F403

SECRET_KEY = "test-secret-key-not-used-in-prod"
DEBUG = False
ALLOWED_HOSTS = ["*", "testserver"]

# --- SQL Server (BI) ---
BI_HOST = "sqlserver.invalid"
BI_PORT = "1433"
BI_DB = "T24_TEST"
BI_USER = "test_user"
BI_PASS = "test_pass"

# --- Oracle (T24) ---
ORACLE_HOST = "oracle.invalid"
ORACLE_PORT = "1521"
ORACLE_SID = "XE"
ORACLE_USER = "test_user"
ORACLE_PASS = "test_pass"

# --- MySQL (rapports) ---
MYSQL_HOST = "mysql.invalid"
MYSQL_PORT = 3306
MYSQL_DB = "ai_agents_test"
MYSQL_USER = "test_user"
MYSQL_PASS = "test_pass"

# --- Ollama ---
OLLAMA_BASE_URL = "http://ollama.invalid:11434"
OLLAMA_MODEL = "qwen2.5:7b"

# --- OAuth2 / AuthMS ---
AUTHMS_URL = "http://authms.invalid:8090"
OAUTH2_JWKS_URL = f"{AUTHMS_URL}/oauth2/jwks"
OAUTH2_ISSUER = AUTHMS_URL

# Pas de base Django : les tests ne doivent jamais demander la fixture `db`.
DATABASES = {}
