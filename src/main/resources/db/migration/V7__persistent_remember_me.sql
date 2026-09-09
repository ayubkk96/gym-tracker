CREATE TABLE persistent_logins (
    username VARCHAR(255) NOT NULL,
    series VARCHAR(64) PRIMARY KEY,
    token VARCHAR(64) NOT NULL,
    last_used TIMESTAMP NOT NULL,
    CONSTRAINT persistent_logins_user_fk
        FOREIGN KEY (username) REFERENCES app_users(email)
        ON UPDATE CASCADE ON DELETE CASCADE
);

CREATE INDEX persistent_logins_username_idx
    ON persistent_logins(username);
