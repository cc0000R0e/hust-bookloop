-- Existing databases only; run once after selecting hust_bookloop.
-- Removes stored nicknames; accounts, passwords and user IDs are retained.
-- New databases created with the current schema.sql must skip this migration.
ALTER TABLE app_user DROP COLUMN nickname;
