-- users table
CREATE TABLE users (
   id BIGINT AUTO_INCREMENT PRIMARY KEY,
   first_name VARCHAR(100) NOT NULL,
   last_name VARCHAR(100) NOT NULL,
   company_name VARCHAR(150),
   email VARCHAR(150) NOT NULL UNIQUE,
   phone VARCHAR(20),
   password VARCHAR(255) NOT NULL,
   enabled BOOLEAN DEFAULT FALSE,
   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- email verification tokens
CREATE TABLE email_verification_token (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  expiry_date TIMESTAMP NOT NULL,
  CONSTRAINT fk_evt_user FOREIGN KEY(user_id) REFERENCES users(id)
);

-- password reset tokens
CREATE TABLE password_reset_token (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(64) NOT NULL UNIQUE,
  user_id BIGINT NOT NULL,
  expiry_date TIMESTAMP NOT NULL,
  CONSTRAINT fk_prt_user FOREIGN KEY(user_id) REFERENCES users(id)
);
