-- Resolution attempts also run outside diligence; they cannot measure its retry fairness.
ALTER TABLE radar_offerings ADD COLUMN last_diligence_attempt_at TIMESTAMP;
UPDATE radar_offerings SET last_diligence_attempt_at = (
    SELECT p.last_refreshed_at FROM radar_diligence_packets p WHERE p.offering_id = radar_offerings.id
);
CREATE INDEX idx_offering_diligence_attempt ON radar_offerings(last_diligence_attempt_at);

ALTER TABLE radar_diligence_financials ADD COLUMN gross_profit NUMERIC(20,2);
ALTER TABLE radar_diligence_financials ADD COLUMN current_assets NUMERIC(20,2);
ALTER TABLE radar_diligence_financials ADD COLUMN current_liabilities NUMERIC(20,2);
ALTER TABLE radar_diligence_financials ADD COLUMN equity NUMERIC(20,2);
ALTER TABLE radar_diligence_financials ADD COLUMN period_ending_date DATE;
