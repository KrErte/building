-- V6: AI Response Cache and Bid Analysis

CREATE TABLE IF NOT EXISTS ai_response_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_key VARCHAR(64) NOT NULL UNIQUE,
    model VARCHAR(100),
    response_text TEXT NOT NULL,
    input_tokens INTEGER,
    output_tokens INTEGER,
    cost_estimate DECIMAL(10,6),
    expires_at TIMESTAMP NOT NULL,
    hit_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_ai_cache_key ON ai_response_cache(cache_key);
CREATE INDEX idx_ai_cache_expires ON ai_response_cache(expires_at);

CREATE TABLE IF NOT EXISTS bid_analyses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id UUID REFERENCES bids(id) ON DELETE CASCADE,
    campaign_id UUID REFERENCES rfq_campaigns(id) ON DELETE CASCADE,
    analysis_type VARCHAR(30) NOT NULL,
    analysis_json TEXT,
    summary TEXT,
    recommendation TEXT,
    confidence_score DECIMAL(5,2),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_bid_analyses_bid_id ON bid_analyses(bid_id);
CREATE INDEX idx_bid_analyses_campaign_id ON bid_analyses(campaign_id);
