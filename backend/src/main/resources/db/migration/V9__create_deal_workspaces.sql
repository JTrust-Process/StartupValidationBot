CREATE TABLE deal_workspaces (
    id               BIGSERIAL PRIMARY KEY,
    payload          JSONB NOT NULL,
    company_name     TEXT NOT NULL,
    platform         TEXT NOT NULL,
    offering_url     TEXT,
    radar_company_id BIGINT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_deal_workspaces_updated_at ON deal_workspaces (updated_at DESC);
CREATE INDEX idx_deal_workspaces_radar_company_id ON deal_workspaces (radar_company_id);
