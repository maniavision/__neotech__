ALTER TABLE service_requests
    ADD COLUMN budget_range VARCHAR(255),
    ADD COLUMN expected_due_date DATE;