-- BuildQuote Database Schema
-- V1: Initial schema with all core tables

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    company_name VARCHAR(255),
    token VARCHAR(64),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Projects table
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(100),
    address TEXT,
    total_budget_min DECIMAL(12,2),
    total_budget_max DECIMAL(12,2),
    deadline DATE,
    status VARCHAR(20) DEFAULT 'DRAFT',
    source_type VARCHAR(20),
    source_file_url TEXT,
    ai_parsed_raw TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Project Stages table
CREATE TABLE IF NOT EXISTS project_stages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    stage_order INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50) NOT NULL,
    description TEXT,
    quantity DECIMAL(10,2),
    unit VARCHAR(10),
    requirements TEXT,
    price_estimate_min DECIMAL(12,2),
    price_estimate_max DECIMAL(12,2),
    price_estimate_median DECIMAL(12,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    depends_on TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Suppliers table
CREATE TABLE IF NOT EXISTS suppliers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    google_place_id VARCHAR(255),
    registry_code VARCHAR(20),
    company_name VARCHAR(255) NOT NULL,
    contact_person VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    website VARCHAR(500),
    address TEXT,
    city VARCHAR(100),
    county VARCHAR(100),
    categories TEXT,
    service_areas TEXT,
    source VARCHAR(50),
    google_rating DECIMAL(2,1),
    google_review_count INT,
    trust_score INT,
    emtak_code VARCHAR(10),
    is_verified BOOLEAN DEFAULT FALSE,
    last_rfq_sent_at TIMESTAMP,
    total_rfqs_sent INT DEFAULT 0,
    total_bids_received INT DEFAULT 0,
    avg_response_time_hours DECIMAL(6,1),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- RFQ Campaigns table
CREATE TABLE IF NOT EXISTS rfq_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stage_id UUID REFERENCES project_stages(id),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255),
    category VARCHAR(50),
    location VARCHAR(100),
    quantity DECIMAL(10,2),
    unit VARCHAR(10),
    specifications TEXT,
    deadline DATE,
    max_budget DECIMAL(12,2),
    status VARCHAR(20) DEFAULT 'DRAFT',
    total_sent INT DEFAULT 0,
    total_delivered INT DEFAULT 0,
    total_opened INT DEFAULT 0,
    total_responded INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    closed_at TIMESTAMP
);

-- RFQ Emails table
CREATE TABLE IF NOT EXISTS rfq_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID REFERENCES rfq_campaigns(id) ON DELETE CASCADE,
    supplier_id UUID,
    supplier_name VARCHAR(255),
    supplier_email VARCHAR(255),
    token VARCHAR(64) UNIQUE NOT NULL,
    status VARCHAR(20) DEFAULT 'QUEUED',
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    opened_at TIMESTAMP,
    responded_at TIMESTAMP,
    reminded_at TIMESTAMP,
    reminder_count INT DEFAULT 0
);

-- Bids table
CREATE TABLE IF NOT EXISTS bids (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_email_id UUID REFERENCES rfq_emails(id),
    campaign_id UUID REFERENCES rfq_campaigns(id),
    supplier_name VARCHAR(255),
    supplier_email VARCHAR(255),
    price DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'EUR',
    timeline_days INT,
    delivery_date DATE,
    notes TEXT,
    line_items TEXT,
    attachments TEXT,
    ai_analysis TEXT,
    status VARCHAR(20) DEFAULT 'RECEIVED',
    submitted_at TIMESTAMP DEFAULT NOW()
);

-- Market Prices table
CREATE TABLE IF NOT EXISTS market_prices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category VARCHAR(50) NOT NULL,
    subcategory VARCHAR(100),
    unit VARCHAR(10) NOT NULL,
    min_price DECIMAL(10,2),
    max_price DECIMAL(10,2),
    median_price DECIMAL(10,2),
    avg_price DECIMAL(10,2),
    sample_count INT DEFAULT 0,
    region VARCHAR(50),
    region_multiplier DECIMAL(3,2) DEFAULT 1.0,
    source VARCHAR(20),
    last_updated TIMESTAMP DEFAULT NOW()
);

-- Supplier Profiles table (for onboarding)
CREATE TABLE IF NOT EXISTS supplier_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) UNIQUE,
    company_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    categories TEXT,
    service_areas TEXT,
    source VARCHAR(20) DEFAULT 'ONBOARDING',
    status VARCHAR(20) DEFAULT 'PENDING',
    registered_at TIMESTAMP DEFAULT NOW()
);

-- Email Log table
CREATE TABLE IF NOT EXISTS email_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient VARCHAR(255),
    subject VARCHAR(500),
    template VARCHAR(50),
    status VARCHAR(20),
    error_message TEXT,
    sent_at TIMESTAMP DEFAULT NOW()
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_suppliers_category ON suppliers(categories);
CREATE INDEX IF NOT EXISTS idx_suppliers_city ON suppliers(city);
CREATE INDEX IF NOT EXISTS idx_rfq_campaigns_status ON rfq_campaigns(status);
CREATE INDEX IF NOT EXISTS idx_rfq_emails_token ON rfq_emails(token);
CREATE INDEX IF NOT EXISTS idx_bids_campaign ON bids(campaign_id);
CREATE INDEX IF NOT EXISTS idx_market_prices_category ON market_prices(category);
