-- 004: Trash. trashed_at is set while a bookmark is in Trash (a soft delete that
-- syncs) and NULL otherwise; deleted_at stays the permanent tombstone.
-- Run ONCE on an existing database, before uploading the new push.php/pull.php.
-- Existing rows are live (NULL). Fresh installs already get this from schema.sql.
ALTER TABLE bookmarks ADD COLUMN trashed_at DATETIME(6) NULL AFTER is_unread;
