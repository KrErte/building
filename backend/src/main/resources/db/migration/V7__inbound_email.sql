-- V7: Inbound Email Processing

CREATE TABLE IF NOT EXISTS inbound_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(500),
    from_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500),
    body TEXT,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    matched_campaign_id UUID REFERENCES rfq_campaigns(id),
    matched_rfq_email_id UUID REFERENCES rfq_emails(id),
    match_strategy VARCHAR(50),
    parsed_bid_id UUID REFERENCES bids(id),
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inbound_emails_from ON inbound_emails(from_email);
CREATE INDEX idx_inbound_emails_processed ON inbound_emails(processed);
CREATE INDEX idx_inbound_emails_message_id ON inbound_emails(message_id);

CREATE TABLE IF NOT EXISTS email_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_email_id UUID NOT NULL REFERENCES rfq_emails(id) ON DELETE CASCADE,
    campaign_id UUID NOT NULL REFERENCES rfq_campaigns(id) ON DELETE CASCADE,
    reminder_number INTEGER NOT NULL DEFAULT 1,
    sent_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_email_reminders_rfq_email ON email_reminders(rfq_email_id);
