-- Modify service_requests table: remove old columns and add price
ALTER TABLE service_requests
    DROP COLUMN admin_notes,
    DROP COLUMN user_name,
    DROP COLUMN user_email,
    ADD COLUMN price DECIMAL(10, 2); -- Added price column, (10 total digits, 2 after decimal)

-- Payments Table
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_request_id VARCHAR(36) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    payment_status VARCHAR(50) NOT NULL, -- e.g., PENDING, COMPLETED, FAILED
    payment_provider VARCHAR(100),       -- e.g., STRIPE, PAYPAL
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (service_request_id) REFERENCES service_requests(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;