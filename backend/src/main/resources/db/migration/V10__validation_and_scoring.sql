-- V10: Validation and scoring fields for phases 1A/1B
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS emtak_code VARCHAR(10);
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS validation_confidence DECIMAL(5,2);
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS validation_issues TEXT;
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS matched_suppliers_json TEXT;

ALTER TABLE projects ADD COLUMN IF NOT EXISTS parse_confidence DECIMAL(5,2);
ALTER TABLE projects ADD COLUMN IF NOT EXISTS validation_status VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_pipelines_status_updated ON pipelines(status, updated_at);
