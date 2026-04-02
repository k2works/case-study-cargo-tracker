CREATE TABLE tracking_numbers (
    tracking_number VARCHAR(12) NOT NULL PRIMARY KEY,
    booking_id      VARCHAR(36) NOT NULL UNIQUE
);
