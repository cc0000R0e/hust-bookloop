-- Existing MySQL databases: run once AFTER 002_account_major.sql.
-- Preserve user accounts, existing book scopes and their IDs.
ALTER TABLE app_user ADD COLUMN college VARCHAR(100) NULL;
ALTER TABLE listing_scope
    DROP CHECK ck_scope_major,
    DROP INDEX uk_scope_combination,
    ADD COLUMN college VARCHAR(100) NULL,
    ADD COLUMN college_key VARCHAR(100) GENERATED ALWAYS AS (COALESCE(college, '')) STORED,
    ADD UNIQUE KEY uk_scope_combination (listing_id, major_key, college_key, semester_code),
    ADD CONSTRAINT ck_scope_major CHECK (
        (scope_type = 'ALL' AND major_id IS NULL AND college IS NULL) OR
        (scope_type = 'SPECIFIC' AND major_id IS NOT NULL AND major_id > 0 AND college IS NULL) OR
        (scope_type = 'COLLEGE' AND major_id IS NULL AND college IS NOT NULL AND CHAR_LENGTH(TRIM(college)) > 0)
    );
