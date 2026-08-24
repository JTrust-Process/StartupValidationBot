ALTER TABLE radar_ai_attempts
    ADD COLUMN actual_model VARCHAR(160);

ALTER TABLE radar_ai_attempts
    ADD COLUMN provider_cost_usd NUMERIC(18, 8);

ALTER TABLE radar_ai_attempts
    ADD COLUMN provider_request_id VARCHAR(160);

ALTER TABLE radar_ai_attempts
    ADD COLUMN provider_trace_id VARCHAR(160);

CREATE INDEX idx_radar_ai_attempts_provider_created
    ON radar_ai_attempts(provider, created_at DESC);
