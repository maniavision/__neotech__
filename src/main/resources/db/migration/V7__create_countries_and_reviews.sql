-- Countries Table
-- Using VARCHAR(2) for primary key is standard for ISO 3166-1 alpha-2 country codes (e.g., US, FR, GB)
CREATE TABLE countries (
   code VARCHAR(2) PRIMARY KEY,
   name VARCHAR(50) NOT NULL,
   continent VARCHAR(2) NOT NULL, -- Suggest using standard codes: AF, AN, AS, EU, NA, OC, SA
   phone_code VARCHAR(10)         -- Increased size slightly to be safe (e.g., +1, +44)
);

-- Reviews Table
CREATE TABLE reviews (
     id BIGINT AUTO_INCREMENT PRIMARY KEY,
     user_id BIGINT NOT NULL,
     service_request_id BIGINT,  -- Optional: links review to a specific completed request
     rating INT NOT NULL,
     comment TEXT,
     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
     FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE SET NULL,
     CONSTRAINT chk_rating CHECK (rating >= 1 AND rating <= 5)
);