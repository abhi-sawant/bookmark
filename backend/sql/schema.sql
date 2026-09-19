-- Bookmark sync API schema
-- InnoDB + utf8mb4 throughout. utf8mb4_bin is used specifically for columns that hold
-- opaque identifiers compared byte-for-byte (id, token_hash). Human-readable text
-- (email, device_name, name) uses utf8mb4_unicode_ci.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- auth_tokens
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_tokens (
    id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       INT UNSIGNED NOT NULL,
    device_name   VARCHAR(100) NOT NULL,
    token_hash    CHAR(64) NOT NULL,
    created_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at  DATETIME(6) NULL,
    revoked_at    DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_auth_tokens_token_hash (token_hash),
    KEY idx_auth_tokens_user_revoked (user_id, revoked_at),
    CONSTRAINT fk_auth_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE auth_tokens MODIFY token_hash CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

-- ---------------------------------------------------------------------------
-- categories
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    user_id     INT UNSIGNED NOT NULL,
    id          VARCHAR(36) NOT NULL,
    name        VARCHAR(40) NOT NULL,
    color_hex   CHAR(7) NOT NULL,
    icon_key    VARCHAR(40) NULL,
    is_default  TINYINT(1) NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    deleted_at  DATETIME(6) NULL,
    PRIMARY KEY (user_id, id),
    KEY idx_categories_user_updated (user_id, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- id is an opaque identifier (UUID) compared byte-for-byte -> utf8mb4_bin.
ALTER TABLE categories MODIFY id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

-- Note: deliberately no unique constraint on `name` -- two offline devices
-- could rename different categories to the same string before syncing.

-- ---------------------------------------------------------------------------
-- bookmarks
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookmarks (
    user_id           INT UNSIGNED NOT NULL,
    id                VARCHAR(36) NOT NULL,
    url               VARCHAR(2048) NOT NULL,
    original_url      VARCHAR(2048) NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       VARCHAR(500) NULL,
    site_name         VARCHAR(60) NULL,
    thumbnail_url     VARCHAR(500) NULL,
    thumbnail_width   SMALLINT UNSIGNED NULL,
    thumbnail_height  SMALLINT UNSIGNED NULL,
    accent_color      INT NULL,
    image_candidates  TEXT NULL,
    category_id       VARCHAR(36) NOT NULL DEFAULT 'unsorted',
    manual_fields     TINYINT UNSIGNED NOT NULL DEFAULT 0,
    is_pinned         TINYINT(1) NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    deleted_at        DATETIME(6) NULL,
    PRIMARY KEY (user_id, id),
    KEY idx_bookmarks_user_updated (user_id, updated_at, id),
    KEY idx_bookmarks_user_url (user_id, url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- id is an opaque identifier (UUID) compared byte-for-byte -> utf8mb4_bin.
ALTER TABLE bookmarks MODIFY id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;
ALTER TABLE bookmarks MODIFY category_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT 'unsorted';

-- Deliberately NO unique constraint on `url` -- two offline devices can
-- independently save the same URL and both rows must coexist after sync.
-- Deliberately NO foreign key from category_id -> categories.id -- enforced
-- in application code (push.php) instead, to avoid a whole push transaction
-- failing on FK ordering across two arrays in one request.
-- Note: metadata_state/failure_cause/fetch_attempts/last_fetch_at are
-- intentionally device-local and never synced -- do not add them here.

-- ---------------------------------------------------------------------------
-- password_resets
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_resets (
    id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     INT UNSIGNED NOT NULL,
    token_hash  CHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at  DATETIME(6) NOT NULL,
    used_at     DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_password_resets_token_hash (token_hash),
    KEY idx_password_resets_user (user_id),
    CONSTRAINT fk_password_resets_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE password_resets MODIFY token_hash CHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

-- ---------------------------------------------------------------------------
-- rate_limit_events
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rate_limit_events (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    action      ENUM('register', 'login', 'forgot_password') NOT NULL,
    identifier  VARCHAR(255) NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_rate_limit_events_lookup (action, identifier, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
