-- V13: Enhanced comparison, negotiation workflow, file hash cache, pipeline retry

-- Negotiation targets from AI comparison
CREATE TABLE IF NOT EXISTS negotiation_targets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_analysis_id UUID REFERENCES bid_analyses(id),
    bid_id UUID REFERENCES bids(id),
    target_price NUMERIC(12,2),
    discount_percent NUMERIC(5,2),
    reasoning TEXT,
    leverage TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Multi-round negotiation tracking
CREATE TABLE IF NOT EXISTS negotiation_rounds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bid_id UUID NOT NULL REFERENCES bids(id) ON DELETE CASCADE,
    round_number INTEGER NOT NULL,
    our_subject VARCHAR(500),
    our_message TEXT,
    their_reply TEXT,
    proposed_price NUMERIC(12,2),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sent_at TIMESTAMP,
    replied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_neg_rounds_bid ON negotiation_rounds(bid_id);

-- File hash cache for avoiding duplicate AI calls
CREATE TABLE IF NOT EXISTS file_hash_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cache_key VARCHAR(64) NOT NULL UNIQUE,
    operation_type VARCHAR(50) NOT NULL,
    prompt_hash VARCHAR(64) NOT NULL,
    response_json TEXT NOT NULL,
    hit_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_fhc_key ON file_hash_cache(cache_key);
CREATE INDEX IF NOT EXISTS idx_fhc_expires ON file_hash_cache(expires_at);

-- Pipeline retry scheduling
ALTER TABLE pipeline_steps ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;
