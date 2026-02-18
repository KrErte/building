-- V11: Email bid ingestion reference codes + risk intelligence fields

-- Phase 2: Email bid ingestion
ALTER TABLE rfq_campaigns ADD COLUMN IF NOT EXISTS reference_code VARCHAR(20) UNIQUE;
ALTER TABLE bids ADD COLUMN IF NOT EXISTS source VARCHAR(20) DEFAULT 'FORM';

-- Phase 3: Estonian registry fields on company_enrichments
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS tax_debt BOOLEAN;
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS tax_debt_amount DECIMAL(12,2);
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS financial_trend VARCHAR(10);
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS annual_revenue DECIMAL(14,2);
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS employee_count INTEGER;
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS years_in_business INTEGER;
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS public_procurement_count INTEGER;
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS registry_data_json TEXT;
ALTER TABLE company_enrichments ADD COLUMN IF NOT EXISTS registry_checked_at TIMESTAMP;
