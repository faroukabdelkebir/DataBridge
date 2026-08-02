-- ============================================================
-- Spring Authorization Server – schema for MySQL
-- ============================================================

CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id                            VARCHAR(100)  NOT NULL,
    client_id                     VARCHAR(100)  NOT NULL,
    client_id_issued_at           TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret                 VARCHAR(200)  DEFAULT NULL,
    client_secret_expires_at      TIMESTAMP     NULL DEFAULT NULL,
    client_name                   VARCHAR(200)  NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types     VARCHAR(1000) NOT NULL,
    redirect_uris                 VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris     VARCHAR(1000) DEFAULT NULL,
    scopes                        VARCHAR(1000) NOT NULL,
    client_settings               VARCHAR(2000) NOT NULL,
    token_settings                VARCHAR(2000) NOT NULL,
    PRIMARY KEY (id)
    );

CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id                            VARCHAR(100)  NOT NULL,
    registered_client_id          VARCHAR(100)  NOT NULL,
    principal_name                VARCHAR(200)  NOT NULL,
    authorization_grant_type      VARCHAR(100)  NOT NULL,
    authorized_scopes             VARCHAR(1000) DEFAULT NULL,
    attributes                    TEXT          DEFAULT NULL,
    state                         VARCHAR(500)  DEFAULT NULL,
    authorization_code_value      TEXT          DEFAULT NULL,
    authorization_code_issued_at  TIMESTAMP     NULL DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP     NULL DEFAULT NULL,
    authorization_code_metadata   TEXT          DEFAULT NULL,
    access_token_value            TEXT          DEFAULT NULL,
    access_token_issued_at        TIMESTAMP     NULL DEFAULT NULL,
    access_token_expires_at       TIMESTAMP     NULL DEFAULT NULL,
    access_token_metadata         TEXT          DEFAULT NULL,
    access_token_type             VARCHAR(100)  DEFAULT NULL,
    access_token_scopes           VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value           TEXT          DEFAULT NULL,
    oidc_id_token_issued_at       TIMESTAMP     NULL DEFAULT NULL,
    oidc_id_token_expires_at      TIMESTAMP     NULL DEFAULT NULL,
    oidc_id_token_metadata        TEXT          DEFAULT NULL,
    refresh_token_value           TEXT          DEFAULT NULL,
    refresh_token_issued_at       TIMESTAMP     NULL DEFAULT NULL,
    refresh_token_expires_at      TIMESTAMP     NULL DEFAULT NULL,
    refresh_token_metadata        TEXT          DEFAULT NULL,
    user_code_value               TEXT          DEFAULT NULL,
    user_code_issued_at           TIMESTAMP     NULL DEFAULT NULL,
    user_code_expires_at          TIMESTAMP     NULL DEFAULT NULL,
    user_code_metadata            TEXT          DEFAULT NULL,
    device_code_value             TEXT          DEFAULT NULL,
    device_code_issued_at         TIMESTAMP     NULL DEFAULT NULL,
    device_code_expires_at        TIMESTAMP     NULL DEFAULT NULL,
    device_code_metadata          TEXT          DEFAULT NULL,
    PRIMARY KEY (id)
    );

CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id  VARCHAR(100)  NOT NULL,
    principal_name        VARCHAR(200)  NOT NULL,
    authorities           VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
    );

-- ── Refresh Tokens (custom – for user sessions) ─────────────
-- CREATE TABLE IF NOT EXISTS refresh_tokens (
--    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
--    token       VARCHAR(512)  NOT NULL,
--    user_id     BIGINT        NOT NULL,
--    expires_at  TIMESTAMP     NOT NULL,
--    revoked     BOOLEAN       NOT NULL DEFAULT FALSE,
#     created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     UNIQUE KEY uk_refresh_token (token),
#     INDEX       idx_rt_user_id (user_id)
#     );

-- ── Password Reset Tokens ────────────────────────────────────
# CREATE TABLE IF NOT EXISTS password_reset_tokens (
#     id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
#     token       VARCHAR(256)  NOT NULL,
#     user_id     BIGINT        NOT NULL,
#     expires_at  TIMESTAMP     NOT NULL,
#     used        BOOLEAN       NOT NULL DEFAULT FALSE,
#     created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     UNIQUE KEY uk_prt_token (token),
#     INDEX       idx_prt_user_id (user_id)
#     );

-- ── Audit Log ────────────────────────────────────────────────
# CREATE TABLE IF NOT EXISTS auth_audit_log (
#     id          BIGINT        NOT NULL  PRIMARY KEY,
#     event_type  VARCHAR(50)   NOT NULL,
#     username    VARCHAR(100)  DEFAULT NULL,
#     ip_address  VARCHAR(45)   DEFAULT NULL,
#     user_agent  VARCHAR(500)  DEFAULT NULL,
#     success     BOOLEAN       NOT NULL,
#     detail      VARCHAR(500)  DEFAULT NULL,
#     created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
#     INDEX idx_aal_username   (username),
#     INDEX idx_aal_event_type (event_type),
#     INDEX idx_aal_created_at (created_at)
#     );