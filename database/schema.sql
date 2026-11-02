-- HUST BookLoop initial schema. Target: MySQL 8.4, InnoDB.
-- Select an EMPTY database before execution. No DROP or account seeding.
-- All application/database connection timestamps use UTC.
SET NAMES utf8mb4;

CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(40) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'USER',
    major_id BIGINT NULL,
    default_contact_type VARCHAR(16) NULL,
    default_contact_value VARCHAR(100) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    CONSTRAINT ck_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_contact CHECK (
        (default_contact_type IS NULL AND default_contact_value IS NULL) OR
        (default_contact_type IS NOT NULL AND default_contact_type IN ('WECHAT','QQ','PHONE')
         AND default_contact_value IS NOT NULL AND CHAR_LENGTH(TRIM(default_contact_value)) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE major (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    colleges VARCHAR(255) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_major_name (name),
    CONSTRAINT ck_major_name CHECK (CHAR_LENGTH(TRIM(name)) > 0 AND name <> '不限专业'),
    CONSTRAINT ck_major_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE app_user ADD CONSTRAINT fk_user_major FOREIGN KEY (major_id) REFERENCES major(id);

CREATE TABLE tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name),
    CONSTRAINT ck_tag_name CHECK (CHAR_LENGTH(TRIM(name)) > 0),
    CONSTRAINT ck_tag_enabled CHECK (enabled IN (0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    category VARCHAR(16) NOT NULL,
    author VARCHAR(100) NULL,
    publisher VARCHAR(100) NULL,
    edition_note VARCHAR(100) NULL,
    course_name VARCHAR(100) NULL,
    description VARCHAR(2000) NULL,
    condition_code VARCHAR(16) NOT NULL,
    note_level VARCHAR(16) NOT NULL,
    transfer_type VARCHAR(16) NOT NULL,
    price DECIMAL(6,2) NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    admin_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    admin_reason VARCHAR(500) NULL,
    completed_at DATETIME(3) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_listing_market (admin_blocked, lifecycle_status, published_at, id),
    KEY ix_listing_owner (owner_id, published_at, id),
    KEY ix_listing_price (admin_blocked, lifecycle_status, price, id),
    CONSTRAINT fk_listing_owner FOREIGN KEY (owner_id) REFERENCES app_user(id),
    CONSTRAINT ck_listing_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_listing_description CHECK (description IS NULL OR CHAR_LENGTH(TRIM(description)) > 0),
    CONSTRAINT ck_listing_category CHECK (category IN ('TEXTBOOK','READING','EXAM')),
    CONSTRAINT ck_listing_condition CHECK (condition_code IN ('LIKE_NEW','LIGHT_USE','HEAVY_USE')),
    CONSTRAINT ck_listing_notes CHECK (note_level IN ('NONE','FEW','MANY')),
    CONSTRAINT ck_listing_price CHECK (
        (transfer_type = 'SALE' AND price > 0 AND price <= 9999.99) OR
        (transfer_type = 'GIFT' AND price = 0)
    ),
    CONSTRAINT ck_listing_lifecycle CHECK (lifecycle_status IN ('ACTIVE','RESERVED','COMPLETED','WITHDRAWN')),
    CONSTRAINT ck_listing_admin CHECK (
        admin_blocked IN (0,1) AND
        (admin_blocked = 0 OR (admin_reason IS NOT NULL AND CHAR_LENGTH(TRIM(admin_reason)) > 0))
    ),
    CONSTRAINT ck_listing_completed CHECK (
        (lifecycle_status = 'COMPLETED' AND completed_at IS NOT NULL) OR
        (lifecycle_status <> 'COMPLETED' AND completed_at IS NULL)
    ),
    CONSTRAINT ck_listing_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing_scope (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    major_id BIGINT NULL,
    semester_code VARCHAR(16) NOT NULL,
    -- NULL means all majors. Normalize only for uniqueness; no fake major row.
    major_key BIGINT GENERATED ALWAYS AS (COALESCE(major_id, 0)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_combination (listing_id, major_key, semester_code),
    KEY ix_scope_filter (semester_code, major_id, listing_id),
    CONSTRAINT fk_scope_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT fk_scope_major FOREIGN KEY (major_id) REFERENCES major(id),
    CONSTRAINT ck_scope_major CHECK (
        (scope_type = 'ALL' AND major_id IS NULL) OR
        (scope_type = 'SPECIFIC' AND major_id IS NOT NULL AND major_id > 0)
    ),
    CONSTRAINT ck_scope_semester CHECK (semester_code IN (
        'Y1_S1','Y1_S2','Y2_S1','Y2_S2','Y3_S1','Y3_S2',
        'Y4_S1','Y4_S2','Y5_S1','Y5_S2','POSTGRAD','UNDEFINED'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing_tag (
    listing_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (listing_id, tag_id),
    KEY ix_listing_tag_reverse (tag_id, listing_id),
    CONSTRAINT fk_lt_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT fk_lt_tag FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing_image (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(32) NOT NULL,
    byte_size INT NOT NULL,
    sort_order TINYINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_image_storage (storage_key),
    UNIQUE KEY uk_image_order (listing_id, sort_order),
    CONSTRAINT fk_image_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT ck_image_mime CHECK (mime_type IN ('image/jpeg','image/png','image/webp')),
    CONSTRAINT ck_image_size CHECK (byte_size BETWEEN 1 AND 5242880),
    CONSTRAINT ck_image_order CHECK (sort_order BETWEEN 1 AND 6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing_contact (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    contact_type VARCHAR(16) NOT NULL,
    contact_value VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_listing_contact (listing_id, contact_type),
    CONSTRAINT fk_contact_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT ck_contact_type CHECK (contact_type IN ('WECHAT','QQ','PHONE')),
    CONSTRAINT ck_contact_value CHECK (CHAR_LENGTH(TRIM(contact_value)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE listing_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(16) NOT NULL,
    action_code VARCHAR(24) NOT NULL,
    before_snapshot JSON NULL,
    after_snapshot JSON NOT NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY ix_event_listing (listing_id, created_at, id),
    KEY ix_event_actor (actor_id),
    CONSTRAINT fk_event_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT fk_event_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    CONSTRAINT ck_event_role CHECK (actor_role IN ('USER','ADMIN')),
    CONSTRAINT ck_event_action CHECK (action_code IN (
        'PUBLISH','RESERVE','CANCEL_RESERVATION','COMPLETE','WITHDRAW',
        'RELIST','CHANGE_TRANSFER','ADMIN_BLOCK','ADMIN_RELEASE'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE report (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    reporter_id BIGINT NOT NULL,
    reason_code VARCHAR(24) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    handler_id BIGINT NULL,
    resolution_note VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    handled_at DATETIME(3) NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    -- UNIQUE permits multiple NULL values, but only one PENDING row per pair.
    pending_key TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_pending (listing_id, reporter_id, pending_key),
    KEY ix_report_queue (status, created_at, id),
    KEY ix_report_reporter (reporter_id),
    KEY ix_report_handler (handler_id),
    CONSTRAINT fk_report_listing FOREIGN KEY (listing_id) REFERENCES listing(id),
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES app_user(id),
    CONSTRAINT fk_report_handler FOREIGN KEY (handler_id) REFERENCES app_user(id),
    CONSTRAINT ck_report_reason CHECK (reason_code IN ('INACCURATE','FALSE_LISTING','INAPPROPRIATE','OTHER')),
    CONSTRAINT ck_report_status CHECK (status IN ('PENDING','RESOLVED','REJECTED')),
    CONSTRAINT ck_report_resolution CHECK (
        (status = 'PENDING' AND handler_id IS NULL AND handled_at IS NULL AND resolution_note IS NULL) OR
        (status IN ('RESOLVED','REJECTED') AND handler_id IS NOT NULL AND handled_at IS NOT NULL
         AND resolution_note IS NOT NULL AND CHAR_LENGTH(TRIM(resolution_note)) > 0)
    ),
    CONSTRAINT ck_report_version CHECK (row_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
