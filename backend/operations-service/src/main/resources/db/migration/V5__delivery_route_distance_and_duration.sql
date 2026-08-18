ALTER TABLE delivery_routes
    ADD COLUMN distance_km NUMERIC(19, 3),
    ADD COLUMN estimated_duration_minutes INTEGER,
    ADD CONSTRAINT ck_delivery_route_distance CHECK (distance_km IS NULL OR distance_km > 0),
    ADD CONSTRAINT ck_delivery_route_duration CHECK (
        estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0);
