-- V8: 3-Tier Company Enrichment

CREATE TABLE IF NOT EXISTS company_enrichments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_id UUID NOT NULL UNIQUE REFERENCES suppliers(id) ON DELETE CASCADE,
    -- Tier 1: Crawler facts
    crawler_facts_json TEXT,
    -- Tier 2: LLM-generated
    llm_summary TEXT,
    llm_specialties TEXT,
    -- Tier 3: Deep analysis
    risk_score INTEGER CHECK (risk_score >= 0 AND risk_score <= 100),
    reliability_score INTEGER CHECK (reliability_score >= 0 AND reliability_score <= 100),
    price_competitiveness VARCHAR(20),
    recommended_for TEXT,
    deep_analysis_json TEXT,
    -- Cache management
    tier1_completed_at TIMESTAMP,
    tier2_completed_at TIMESTAMP,
    tier3_completed_at TIMESTAMP,
    cache_expires_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_company_enrichments_supplier ON company_enrichments(supplier_id);
CREATE INDEX idx_company_enrichments_cache ON company_enrichments(cache_expires_at);
