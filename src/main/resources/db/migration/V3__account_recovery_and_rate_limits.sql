ALTER TABLE app_users ADD COLUMN password_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    user_id BIGINT PRIMARY KEY REFERENCES app_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX password_reset_expiry_idx ON password_reset_tokens(expires_at);

CREATE TABLE auth_rate_limits (
    bucket_hash VARCHAR(64) PRIMARY KEY,
    attempts INTEGER NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX auth_rate_limit_expiry_idx ON auth_rate_limits(expires_at);
