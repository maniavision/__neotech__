ALTER TABLE email_verification_token
    ADD COLUMN temp_password VARCHAR(255);

ALTER TABLE password_reset_token
    ADD COLUMN temp_password VARCHAR(255);