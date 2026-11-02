-- 002: bookmark tags (newline-joined list, up to 10 tags of 30 characters).
-- Run ONCE on an existing database, before uploading the new push.php/pull.php.
-- Fresh installs already get this column from schema.sql.
-- (If phpMyAdmin says "Duplicate column name 'tags'", it was already applied.)
ALTER TABLE bookmarks ADD COLUMN tags TEXT NULL AFTER is_pinned;
