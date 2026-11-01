-- Make the listing description optional. Run once on databases created with the original schema.
-- Stop the application while executing this migration.

ALTER TABLE listing DROP CHECK ck_listing_description;

ALTER TABLE listing MODIFY description VARCHAR(2000) NULL;

ALTER TABLE listing
    ADD CONSTRAINT ck_listing_description CHECK (description IS NULL OR CHAR_LENGTH(TRIM(description)) > 0);
