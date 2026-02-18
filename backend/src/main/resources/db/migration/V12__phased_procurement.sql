-- V12: Phased procurement timeline fields
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS planned_start_date DATE;
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS planned_duration_days INTEGER;
ALTER TABLE project_stages ADD COLUMN IF NOT EXISTS procurement_status VARCHAR(20) DEFAULT 'ACTIVE';

ALTER TABLE projects ADD COLUMN IF NOT EXISTS quoting_horizon_days INTEGER DEFAULT 90;
ALTER TABLE projects ADD COLUMN IF NOT EXISTS construction_start_date DATE;
