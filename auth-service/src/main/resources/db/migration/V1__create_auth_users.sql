CREATE TABLE auth_users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    role          VARCHAR(20)              NOT NULL,
    enabled       BOOLEAN                  NOT NULL DEFAULT TRUE,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_auth_users_email
        UNIQUE (email),

    CONSTRAINT chk_auth_users_email_normalized
        CHECK (email <> '' AND email = LOWER(BTRIM(email))),

    CONSTRAINT chk_auth_user_role
        CHECK (role IN ('USER', 'ADMIN'))
);
