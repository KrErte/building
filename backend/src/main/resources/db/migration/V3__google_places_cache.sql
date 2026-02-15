-- Google Places Cache table for faster supplier searches
CREATE TABLE google_places_cache (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    location VARCHAR(100) NOT NULL,
    results_json TEXT,
    result_count INTEGER,
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP,
    use_count INTEGER DEFAULT 0
);

-- Index for fast lookups by category+location
CREATE INDEX idx_cache_category_location ON google_places_cache(category, location);

-- Index for cache expiry cleanup
CREATE INDEX idx_cache_searched_at ON google_places_cache(searched_at);

-- Comment
COMMENT ON TABLE google_places_cache IS 'Caches Google Places API results for 7 days to speed up supplier searches';
