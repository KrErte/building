-- V9: Multi-Tenancy, Rate Limiting, and API Usage Tracking

-- Organizations
CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(100) NOT NULL UNIQUE,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    owner_id UUID NOT NULL REFERENCES users(id),
    max_members INTEGER DEFAULT 5,
    max_projects_per_month INTEGER DEFAULT 10,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_organizations_slug ON organizations(slug);
CREATE INDEX idx_organizations_owner ON organizations(owner_id);

-- Organization members
CREATE TABLE IF NOT EXISTS organization_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(organization_id, user_id)
);
CREATE INDEX idx_org_members_user ON organization_members(user_id);
CREATE INDEX idx_org_members_org ON organization_members(organization_id);

-- API usage logging
CREATE TABLE IF NOT EXISTS api_usage_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    organization_id UUID REFERENCES organizations(id),
    endpoint VARCHAR(500) NOT NULL,
    method VARCHAR(10) NOT NULL,
    status_code INTEGER,
    response_time_ms BIGINT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_api_usage_created ON api_usage_log(created_at);
CREATE INDEX idx_api_usage_user ON api_usage_log(user_id);

-- Rate limit buckets
CREATE TABLE IF NOT EXISTS rate_limit_buckets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bucket_key VARCHAR(255) NOT NULL,
    window_start TIMESTAMP NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 1,
    UNIQUE(bucket_key, window_start)
);
CREATE INDEX idx_rate_limit_key ON rate_limit_buckets(bucket_key);

-- Add organization reference to users
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'current_organization_id') THEN
        ALTER TABLE users ADD COLUMN current_organization_id UUID REFERENCES organizations(id);
    END IF;
END $$;

-- Add organization reference to projects
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'projects' AND column_name = 'organization_id') THEN
        ALTER TABLE projects ADD COLUMN organization_id UUID REFERENCES organizations(id);
    END IF;
END $$;
