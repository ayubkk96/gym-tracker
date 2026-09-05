ALTER TABLE app_users
    ADD COLUMN password_hash VARCHAR(100);

CREATE UNIQUE INDEX uq_app_users_email_lower
    ON app_users (LOWER(email));
