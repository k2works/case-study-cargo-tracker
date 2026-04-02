ALTER TABLE bookings ADD COLUMN assigned_voyage_no VARCHAR(50);
ALTER TABLE bookings ADD COLUMN route_path        VARCHAR(500);
ALTER TABLE bookings ADD COLUMN estimated_arrival DATE;
