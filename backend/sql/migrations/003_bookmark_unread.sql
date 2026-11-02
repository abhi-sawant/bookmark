-- 003: unread flag (a saved bookmark that has not been opened yet).
-- Run ONCE on an existing database, before uploading the new push.php/pull.php.
-- Existing rows default to 0 (read), so nothing shows as unread after the upgrade.
-- Fresh installs already get this column from schema.sql.
ALTER TABLE bookmarks ADD COLUMN is_unread TINYINT(1) NOT NULL DEFAULT 0 AFTER tags;
