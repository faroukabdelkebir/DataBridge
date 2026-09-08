
# --------------------------------------------
# Étape 1 — Build des dépendances Python
# (pyodbc doit être compilé : gcc + unixodbc-dev)
# --------------------------------------------
FROM python:3.12-slim-bullseye AS Builder

ENV PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1


RUN echo 'Acquire::Retries "3";' > /etc/apt/apt.conf.d/80-retries \
    && apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        unixodbc-dev \
    && rm -rf /var/lib/apt/lists/*

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

COPY requirements.txt .
RUN pip install --upgrade pip \
    && pip install -r requirements.txt \
    # Serveur WSGI de production (absent de requirements.txt, dev-only là-bas)
    && pip install gunicorn==23.0.0

# --------------------------------------------
# Étape 2 — Image finale
# --------------------------------------------
FROM python:3.12-slim-bullseye

# Pilote ODBC 17 pour SQL Server : les chaînes de connexion du code utilisent
# littéralement "DRIVER={ODBC Driver 17 for SQL Server}" (voir core/optimizations.py).
# C'est la raison du choix de la base bullseye : msodbcsql17 n'est pas publié
# par Microsoft pour Debian 12 (bookworm).


RUN echo 'Acquire::Retries "3";' > /etc/apt/apt.conf.d/80-retries \
    && apt-get update && apt-get install -y --no-install-recommends \
        curl gnupg apt-transport-https ca-certificates \
    && curl -fsSL https://packages.microsoft.com/keys/microsoft.asc \
        | gpg --dearmor -o /usr/share/keyrings/microsoft-prod.gpg \
    && echo "deb [arch=amd64,arm64 signed-by=/usr/share/keyrings/microsoft-prod.gpg] https://packages.microsoft.com/debian/11/prod bullseye main" \
        > /etc/apt/sources.list.d/mssql-release.list \
    && apt-get update \
    && ACCEPT_EULA=Y apt-get install -y --no-install-recommends msodbcsql17 unixodbc \
    && apt-get purge -y --auto-remove gnupg apt-transport-https \
    && rm -rf /var/lib/apt/lists/*

# Oracle (oracledb) tourne en mode "thin" : aucun Oracle Instant Client requis.
# MySQL passe par PyMySQL (pur Python) : aucun client natif requis.

ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    DJANGO_SETTINGS_MODULE=settings

COPY --from=builder /opt/venv /opt/venv

WORKDIR /app

RUN useradd --create-home --uid 1000 appuser
COPY --chown=appuser:appuser . /app
USER appuser

EXPOSE 65015

# GET / est public (voir urls.py) et renvoie l'état du service + d'Ollama
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:65015/ || exit 1

# Timeout large : les appels au SLM (Ollama) peuvent être longs
CMD ["gunicorn", "wsgi:application", \
     "--bind", "0.0.0.0:65015", \
     "--workers", "3", \
     "--timeout", "300", \
     "--access-logfile", "-", \
     "--error-logfile", "-"]
