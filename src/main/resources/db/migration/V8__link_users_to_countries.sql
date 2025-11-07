ALTER TABLE users
    ADD COLUMN country_code VARCHAR(2),
    ADD CONSTRAINT fk_users_country
    FOREIGN KEY (country_code) REFERENCES countries(code);