"""
Stockage des rapports/résultats des agents dans MySQL.

Même philosophie que les tools (connexion brute, pas d'ORM Django) :
on ouvre une connexion PyMySQL à la demande à partir des variables
d'environnement (voir settings.MYSQL_*).

La base et la table sont créées automatiquement au premier appel
(init_schema), donc aucun script SQL manuel n'est nécessaire.
"""

import json
import logging
import threading

import pymysql
from django.conf import settings

logger = logging.getLogger("agents")

# init_schema() est coûteux (CREATE DATABASE/TABLE) : on ne le fait qu'une
# fois par process, protégé par un verrou pour rester thread-safe (les vues
# scheduler lancent des threads).
_schema_ready = False
_schema_lock = threading.Lock()


def _connect(with_db: bool = True):
    """Ouvre une connexion MySQL. with_db=False pour créer la base elle-même."""
    return pymysql.connect(
        host=settings.MYSQL_HOST,
        port=settings.MYSQL_PORT,
        user=settings.MYSQL_USER,
        password=settings.MYSQL_PASS,
        database=settings.MYSQL_DB if with_db else None,
        charset="utf8mb4",
        autocommit=True,
        connect_timeout=5,
        cursorclass=pymysql.cursors.DictCursor,
    )


_CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS agent_reports (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent        VARCHAR(32)  NOT NULL,           -- 'anomaly_quality' | 'scheduler'
    report_type  VARCHAR(64)  NOT NULL,           -- 'audit_table', 'audit_all', 'quick_score', ...
    target       VARCHAR(128) NULL,               -- table auditée / batch_id concerné
    model        VARCHAR(64)  NULL,               -- modèle SLM utilisé
    status       VARCHAR(32)  NOT NULL DEFAULT 'completed',
    report       JSON         NULL,               -- rapport complet renvoyé par l'agent
    error        TEXT         NULL,
    duration_sec DOUBLE       NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_agent (agent),
    INDEX idx_type (report_type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
"""


def init_schema():
    """Crée la base (si absente) puis la table agent_reports. Idempotent."""
    global _schema_ready
    if _schema_ready:
        return
    with _schema_lock:
        if _schema_ready:
            return
        # 1) La base peut ne pas exister encore → on se connecte sans la
        #    sélectionner pour pouvoir la créer.
        conn = _connect(with_db=False)
        try:
            with conn.cursor() as cur:
                cur.execute(
                    f"CREATE DATABASE IF NOT EXISTS `{settings.MYSQL_DB}` "
                    "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
                )
                cur.execute(f"USE `{settings.MYSQL_DB}`;")
                cur.execute(_CREATE_TABLE)
        finally:
            conn.close()
        _schema_ready = True
        logger.info("[reports_store] Schéma MySQL prêt (%s.agent_reports)", settings.MYSQL_DB)


def save_report(
    agent: str,
    report_type: str,
    report,
    *,
    target: str | None = None,
    model: str | None = None,
    status: str = "completed",
    error: str | None = None,
    duration_sec: float | None = None,
) -> int | None:
    """
    Persiste un résultat d'agent. Renvoie l'id inséré, ou None en cas d'échec.

    La persistance ne doit JAMAIS casser la réponse HTTP de l'agent : toute
    erreur MySQL est loggée puis avalée (l'appelant reçoit None).
    """
    try:
        init_schema()
        conn = _connect()
        try:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    INSERT INTO agent_reports
                        (agent, report_type, target, model, status, report, error, duration_sec)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        agent,
                        report_type,
                        target,
                        model,
                        status,
                        json.dumps(report, default=str, ensure_ascii=False)
                        if report is not None else None,
                        error,
                        duration_sec,
                    ),
                )
                return cur.lastrowid
        finally:
            conn.close()
    except Exception as e:  # noqa: BLE001 — persistance best-effort
        logger.error("[reports_store] Échec de sauvegarde du rapport : %s", e)
        return None


def list_reports(agent: str | None = None, report_type: str | None = None,
                 limit: int = 50) -> list[dict]:
    """Liste les rapports récents (sans le corps `report`, pour rester léger)."""
    init_schema()
    conn = _connect()
    try:
        clauses, params = [], []
        if agent:
            clauses.append("agent = %s")
            params.append(agent)
        if report_type:
            clauses.append("report_type = %s")
            params.append(report_type)
        where = f"WHERE {' AND '.join(clauses)}" if clauses else ""
        params.append(max(1, min(limit, 500)))
        with conn.cursor() as cur:
            cur.execute(
                f"""
                SELECT id, agent, report_type, target, model, status,
                       error, duration_sec, created_at
                FROM agent_reports
                {where}
                ORDER BY id DESC
                LIMIT %s
                """,
                params,
            )
            return cur.fetchall()
    finally:
        conn.close()


def get_report(report_id: int) -> dict | None:
    """Renvoie un rapport complet (avec son corps `report` désérialisé)."""
    init_schema()
    conn = _connect()
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT * FROM agent_reports WHERE id = %s", (report_id,))
            row = cur.fetchone()
            if row and row.get("report"):
                try:
                    row["report"] = json.loads(row["report"])
                except (TypeError, ValueError):
                    pass
            return row
    finally:
        conn.close()
