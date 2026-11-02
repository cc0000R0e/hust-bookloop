-- Run once on an existing MySQL database before starting the new application.
-- Fresh databases use schema.sql instead.
ALTER TABLE major ADD COLUMN colleges VARCHAR(255) NULL;
ALTER TABLE app_user ADD COLUMN major_id BIGINT NULL,
    ADD CONSTRAINT fk_user_major FOREIGN KEY (major_id) REFERENCES major(id);
