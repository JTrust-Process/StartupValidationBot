ALTER TABLE radar_research_sources
    ADD COLUMN evidence_classification VARCHAR(40) NOT NULL DEFAULT 'UNKNOWN';

UPDATE radar_research_sources
SET evidence_classification = 'PUBLIC_NEWS'
WHERE source_type IN ('RSS', 'PRODUCT_HUNT', 'HACKER_NEWS');

ALTER TABLE radar_ai_attempts
    ADD COLUMN http_status INTEGER;

ALTER TABLE radar_ai_attempts
    ADD COLUMN provider_error_type VARCHAR(160);

ALTER TABLE radar_ai_attempts
    ADD COLUMN provider_error_code VARCHAR(160);
