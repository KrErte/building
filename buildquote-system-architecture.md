# BuildQuote — Full System Architecture & Implementation Plan

## Executive Summary

BuildQuote's end goal is a **fully automated construction procurement engine**: a user describes a project (via text, file upload, or photo), the system decomposes it into construction stages, derives technical requirements for each stage, discovers and contacts relevant subcontractors, collects price quotes, and presents a ranked comparison — with minimal human intervention.

This document assesses the current state, identifies gaps, and provides a detailed implementation roadmap to reach full automation.

---

## 1. The End-to-End Target Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        USER INPUT LAYER                             │
│                                                                     │
│   Text Description ──┐                                              │
│   PDF/DOCX Upload ───┼──→  AI PROJECT PARSER  ──→  Structured       │
│   Photo Upload ──────┘     (Claude Sonnet)          Project Model   │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    PROJECT DECOMPOSITION                            │
│                                                                     │
│   Project: "3-room apartment renovation, Tallinn"                   │
│   ├── Stage 1: Demolition (bathroom, 10m²)                         │
│   ├── Stage 2: Electrical (full apartment, 65m²)                   │
│   ├── Stage 3: Plumbing (bathroom + kitchen, 15m²)                 │
│   ├── Stage 4: Tiling (bathroom, 20m²)                             │
│   ├── Stage 5: Flooring (laminate, 45m²)                           │
│   └── Stage 6: Finishing/Painting (walls, 180m²)                   │
│                                                                     │
│   Each stage has:                                                   │
│   - Category (maps to EMTAK / Google Places search terms)           │
│   - Estimated quantity + unit (m², tk, jm, h)                      │
│   - Derived technical requirements                                  │
│   - AI price estimate (from market_prices DB)                       │
│   - Dependency order (demolition before tiling)                     │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  REQUIREMENTS DERIVATION (per stage)                │
│                                                                     │
│   Stage: "Tiling, bathroom, 20m²"                                  │
│   AI generates:                                                     │
│   - Materials needed: tiles, adhesive, grout, waterproofing         │
│   - Surface preparation: leveling, waterproofing membrane           │
│   - Standards: EVS-EN 14411 (ceramic tiles)                        │
│   - Minimum qualifications: Cat III construction license            │
│   - Estimated timeline: 5-7 working days                           │
│   - Questions for contractor: tile type? pattern? underfloor heat? │
│                                                                     │
│   This becomes the RFQ specification document (auto-generated)      │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SUPPLIER DISCOVERY (per stage)                   │
│                                                                     │
│   Data Sources (merged, deduplicated):                              │
│   ├── Google Places API (real-time, rated, with contacts)          │
│   ├── FIE Register (avaandmed.rik.ee, EMTAK 41-43)                │
│   ├── Verified Suppliers (self-registered via /onboard)             │
│   ├── Procurement Winners (riigihanked.riik.ee)                    │
│   └── Internal DB (previously found + user-added)                  │
│                                                                     │
│   Matching logic:                                                   │
│   - Category match (EMTAK code → stage category)                   │
│   - Location match (service area ≤ 50km from project)              │
│   - Availability (not currently overloaded with RFQs)               │
│   - Trust Score ranking (rating + reviews + procurement history)    │
│                                                                     │
│   Result: 20-50 matched suppliers per stage                         │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     AUTOMATED RFQ DISPATCH                          │
│                                                                     │
│   For each stage × matched suppliers:                               │
│   - Generate personalized RFQ email from template                   │
│   - Include: project specs, requirements, deadline, response link   │
│   - Each email has unique tracking token                            │
│   - Channels: Email (primary), SMS (fallback), WhatsApp (future)   │
│   - Rate-limited: 10/min to avoid spam flags                       │
│   - Dev mode: all → kristo.erte@gmail.com                          │
│                                                                     │
│   Example email:                                                    │
│   Subject: "Hinnapäring: Plaatimistööd, Tallinn (20m²)"           │
│   Body: Project details + specs + "Esita pakkumine" button         │
│                                                                     │
│   Campaign tracking:                                                │
│   - QUEUED → SENT → DELIVERED → OPENED → RESPONDED                │
│   - Auto-reminder at day 3 if no response                          │
│   - Auto-close at day 7                                             │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     BID COLLECTION                                  │
│                                                                     │
│   Public page: /bid/{token} (no auth required)                     │
│   Supplier sees:                                                    │
│   - Job details and specifications                                  │
│   - Required deliverables                                           │
│   - Deadline                                                        │
│   - Form: price (EUR), timeline (days), notes, line-item breakdown │
│                                                                     │
│   On submit:                                                        │
│   - Stored in bids table                                            │
│   - User notified (email + in-app)                                  │
│   - AI analyzes bid vs market price → flags anomalies              │
│   - Dashboard updates in real-time                                  │
│                                                                     │
└─────────────────────────────┬───────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  COMPARISON & DECISION SUPPORT                      │
│                                                                     │
│   Per-stage comparison:                                             │
│   - Side-by-side: price, timeline, rating, trust score             │
│   - AI flags: "🔴 68% over market" / "🟢 Best value"              │
│   - Normalized score: (price × 0.4 + time × 0.3 + trust × 0.3)   │
│                                                                     │
│   Project-level summary:                                            │
│   - Total cost range (sum of all stage selections)                  │
│   - Critical path timeline (considering stage dependencies)         │
│   - Recommended combination (AI-optimized for price+quality)        │
│                                                                     │
│   Actions:                                                          │
│   - "Accept bid" → sends acceptance email to winner                │
│   - "Decline bid" → sends polite rejection                         │
│   - "Negotiate" → opens counter-offer dialog                       │
│   - "Request revision" → asks for updated quote with changes       │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Current State Assessment

### What EXISTS and WORKS

| Component | Status | Notes |
|-----------|--------|-------|
| Angular 19 SPA frontend | ✅ Working | Dark theme, purple accents, deployed |
| Spring Boot backend | ✅ Working | JWT auth, REST API, PostgreSQL |
| Auth system | ✅ Working | Register, login, JWT tokens |
| i18n (ET/EN/RU) | ⚠️ Partial | Landing page good, dashboard/forms have leaks |
| Landing page | ✅ Working | Hero, features, pricing, FAQ |
| Dashboard | ✅ Working | Stats, quick actions, supplier cards |
| 3-step Wizard | ✅ Working | Job input → supplier selection → comparison |
| Google Places API | ✅ Working | Finds real Estonian companies with contacts |
| FIE Register import | ✅ Working | 200+ construction FIEs imported |
| AI Price Intelligence | ✅ Working | market_prices table, /api/prices/check endpoint |
| Supplier onboarding | ✅ Working | /onboard/{token} page, email invites |
| Email service (Resend) | ⚠️ Partial | Configured, DEV mode exists, delivery unverified |
| Crawler framework | ⚠️ Exists | Code present but teatmik.ee blocks with CAPTCHA |
| Docker deployment | ✅ Working | Frontend + backend + DB on 37.60.225.35 |
| Paginated companies list | 🔴 Not built | Aleksei requested this |

### What's MISSING for Full Automation

| Component | Priority | Complexity | Current Gap |
|-----------|----------|------------|-------------|
| AI Project Parser | 🔴 Critical | High | No text/file → stages parsing exists |
| Requirements Derivation | 🔴 Critical | Medium | No spec generation per stage |
| Multi-stage project model | 🔴 Critical | Medium | DB schema exists only for single RFQs |
| Mass RFQ dispatch (verified) | 🔴 Critical | Medium | Email service exists but end-to-end untested |
| Bid response page (/bid/{token}) | 🔴 Critical | Medium | Designed but not verified working |
| Bid collection + storage | 🔴 Critical | Medium | Table exists, collection flow untested |
| Comparison dashboard (real bids) | 🟡 High | Medium | Wizard Step 3 shows mock comparison only |
| Supplier matching algorithm | 🟡 High | Medium | Currently just Google search, no scoring |
| Campaign management | 🟡 High | Medium | No tracking of SENT/OPENED/RESPONDED |
| Auto-reminders | 🟢 Medium | Low | Scheduled job needed |
| File upload (PDF/DOCX) | 🟢 Medium | Low | Endpoint doesn't exist |
| Photo → project parsing | 🟢 Medium | Medium | Vision API integration needed |
| Reverse auction | 🟢 Low | High | Future feature |
| WhatsApp/SMS channel | 🟢 Low | Medium | Future feature |

---

## 3. Database Schema (Target State)

### Core Tables

```sql
-- Projects: the top-level container
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    location VARCHAR(100),
    address TEXT,
    total_budget_min DECIMAL(12,2),
    total_budget_max DECIMAL(12,2),
    deadline DATE,
    status VARCHAR(20) DEFAULT 'DRAFT',  -- DRAFT, ACTIVE, COMPLETED, CANCELLED
    source_type VARCHAR(20),  -- TEXT, PDF, DOCX, PHOTO
    source_file_url TEXT,
    ai_parsed_raw JSONB,  -- Raw AI parsing output for debugging
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Project Stages: individual work packages within a project
CREATE TABLE project_stages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE CASCADE,
    stage_order INT NOT NULL,  -- execution sequence
    name VARCHAR(255) NOT NULL,  -- "Plaatimistööd"
    category VARCHAR(50) NOT NULL,  -- TILING, ELECTRICAL, etc.
    description TEXT,
    quantity DECIMAL(10,2),
    unit VARCHAR(10),  -- m2, tk, jm, h
    requirements JSONB,  -- AI-derived specifications
    price_estimate_min DECIMAL(12,2),
    price_estimate_max DECIMAL(12,2),
    price_estimate_median DECIMAL(12,2),
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, RFQ_SENT, BIDS_RECEIVED, AWARDED, COMPLETED
    depends_on UUID[],  -- stage IDs that must complete first
    created_at TIMESTAMP DEFAULT NOW()
);

-- RFQ Campaigns: one per project stage
CREATE TABLE rfq_campaigns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stage_id UUID REFERENCES project_stages(id),
    user_id UUID REFERENCES users(id),
    title VARCHAR(255),
    specifications TEXT,  -- AI-generated requirements document
    deadline DATE,
    max_budget DECIMAL(12,2),
    status VARCHAR(20) DEFAULT 'DRAFT',  -- DRAFT, SENDING, ACTIVE, CLOSED, AWARDED
    total_sent INT DEFAULT 0,
    total_delivered INT DEFAULT 0,
    total_opened INT DEFAULT 0,
    total_responded INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    closed_at TIMESTAMP
);

-- Individual RFQ emails sent to suppliers
CREATE TABLE rfq_emails (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id UUID REFERENCES rfq_campaigns(id),
    supplier_id UUID,  -- references unified supplier view
    supplier_name VARCHAR(255),
    supplier_email VARCHAR(255),
    token VARCHAR(64) UNIQUE NOT NULL,  -- unique response token
    status VARCHAR(20) DEFAULT 'QUEUED',  -- QUEUED, SENT, DELIVERED, BOUNCED, OPENED, RESPONDED
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    opened_at TIMESTAMP,
    responded_at TIMESTAMP,
    reminded_at TIMESTAMP,
    reminder_count INT DEFAULT 0
);

-- Bids received from suppliers
CREATE TABLE bids (
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
    line_items JSONB,  -- detailed breakdown [{item, qty, unit_price, total}]
    attachments JSONB,  -- file URLs
    ai_analysis JSONB,  -- {verdict, percentFromMedian, flags}
    status VARCHAR(20) DEFAULT 'RECEIVED',  -- RECEIVED, UNDER_REVIEW, ACCEPTED, DECLINED, COUNTER_OFFERED
    submitted_at TIMESTAMP DEFAULT NOW()
);

-- Unified supplier data (merged from all sources)
CREATE TABLE suppliers_unified (
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
    categories TEXT[],  -- ['TILING', 'FINISHING']
    service_areas TEXT[],  -- ['TALLINN', 'HARJUMAA']
    source VARCHAR(20),  -- GOOGLE_PLACES, FIE_REGISTER, VERIFIED, PROCUREMENT, SEED
    google_rating DECIMAL(2,1),
    google_review_count INT,
    trust_score INT,  -- 0-100 computed score
    emtak_code VARCHAR(10),
    is_verified BOOLEAN DEFAULT FALSE,
    last_rfq_sent_at TIMESTAMP,
    total_rfqs_sent INT DEFAULT 0,
    total_bids_received INT DEFAULT 0,
    avg_response_time_hours DECIMAL(6,1),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Market prices for AI price intelligence
CREATE TABLE market_prices (
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
    source VARCHAR(20),  -- SEED, USER_BID, PROCUREMENT
    last_updated TIMESTAMP DEFAULT NOW()
);

-- Supplier onboarding profiles
CREATE TABLE supplier_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) UNIQUE,
    company_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    categories TEXT[],
    service_areas TEXT[],
    source VARCHAR(20) DEFAULT 'ONBOARDING',
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, REGISTERED
    registered_at TIMESTAMP DEFAULT NOW()
);

-- Email sending log
CREATE TABLE email_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient VARCHAR(255),
    subject VARCHAR(500),
    template VARCHAR(50),
    status VARCHAR(20),  -- SENT, DELIVERED, BOUNCED, FAILED
    error_message TEXT,
    sent_at TIMESTAMP DEFAULT NOW()
);
```

---

## 4. Implementation Roadmap

### Phase 1: Core Pipeline (Week 1-2) — "Make it work end-to-end"

**Goal:** A user types a project description → system finds suppliers → sends RFQs → collects bids → shows comparison. Even if rough, the full pipeline must work.

#### 1.1 AI Project Parser Service
```
Input: Free text OR uploaded file (PDF/DOCX/TXT)
Processing: Anthropic Claude Sonnet API call
Output: Structured ProjectParseResult with stages
```

The parser prompt template:
```
You are a construction project analyzer. Given a project description, 
extract ALL construction stages needed. For each stage provide:
- name: Estonian name of the work
- category: one of [GENERAL_CONSTRUCTION, ELECTRICAL, PLUMBING, TILING, 
  FINISHING, ROOFING, FACADE, LANDSCAPING, DEMOLITION, FLOORING, 
  HVAC, WINDOWS_DOORS, OTHER]
- quantity: estimated amount
- unit: m2, tk, jm, or h
- description: what specifically needs to be done
- dependencies: which other stages must be done first

Also extract:
- projectTitle: short title
- location: city/address if mentioned
- totalBudget: if mentioned
- deadline: if mentioned

Return ONLY valid JSON.
```

Spring Boot service:
```java
@Service
public class ProjectParserService {
    
    private final AnthropicClient anthropicClient;
    
    public ProjectParseResult parseFromText(String description) {
        // Call Claude Sonnet with construction parsing prompt
        // Return structured stages
    }
    
    public ProjectParseResult parseFromFile(MultipartFile file) {
        // Extract text from PDF (Apache PDFBox) or DOCX (Apache POI)
        // Then call parseFromText()
    }
    
    public ProjectParseResult parseFromImage(MultipartFile image) {
        // Send image to Claude Vision
        // Extract project details from photo/plan
    }
}
```

#### 1.2 Requirements Derivation Engine
```
Input: Stage (category + quantity + description)
Output: Technical specifications + RFQ document text
```

For each parsed stage, a second AI call generates:
- Material specifications
- Surface preparation requirements  
- Applicable Estonian standards (EVS-EN)
- Minimum contractor qualifications
- Estimated timeline
- Key questions for contractor

This becomes the RFQ specification body.

#### 1.3 Multi-Stage Project Management
- Create project with multiple stages
- Each stage independently tracks its RFQ campaign
- Stage dependencies (can't tile before waterproofing)
- Project-level cost aggregation

#### 1.4 Verified End-to-End RFQ Flow
- Send real emails (via Resend)
- Verify /bid/{token} page works
- Verify bid submission stores correctly
- Verify user sees bids in dashboard
- Test the full cycle with kristo.erte@gmail.com

### Phase 2: Scale Supplier Data (Week 2-3)

#### 2.1 Batch Google Places Harvesting
- 12 categories × 10 cities = 120 searches
- Run as background job, 1 req/sec
- Deduplicate by google_place_id
- Target: 1000+ unique companies

#### 2.2 Unified Supplier View
- Merge all sources into suppliers_unified table
- Deduplicate by name similarity (Levenshtein) + address
- Enrich: Google data + FIE data + onboarding data
- Calculate trust_score

#### 2.3 Paginated Companies Page
- /companies with search, filter, sort, pagination
- Source badges (Google, FIE, Verified, Procurement)
- Export to CSV

#### 2.4 Smart Supplier Matching
Instead of just "search by category + location", build a scoring algorithm:
```
MatchScore = (
    categoryMatch × 0.30 +     // exact category vs broad
    locationProximity × 0.25 +  // distance from project
    trustScore × 0.20 +         // rating + reviews + procurement history
    responseRate × 0.15 +       // historical bid response rate
    priceCompetitiveness × 0.10 // historical bid vs market median
)
```

### Phase 3: Intelligence Layer (Week 3-4)

#### 3.1 Bid Analysis AI
When a bid arrives:
- Compare to market_prices → flag if anomalous
- Compare to other bids in same campaign
- Check supplier history (do they always bid high?)
- Generate verdict: GREAT_DEAL / FAIR / OVERPRICED / RED_FLAG

#### 3.2 Learning Market Prices
Every real bid updates market_prices:
```java
public void updateMarketPrices(Bid bid, Stage stage) {
    MarketPrice mp = findByCategory(stage.getCategory(), stage.getLocation());
    mp.addSample(bid.getPrice() / stage.getQuantity()); // price per unit
    mp.recalculate(); // weighted: recent bids 2x weight
    mp.setSampleCount(mp.getSampleCount() + 1);
    save(mp);
}
```

#### 3.3 Campaign Analytics
- Open rate, response rate, average response time
- Best day/time to send RFQs
- Which supplier categories respond fastest
- Conversion funnel: Sent → Opened → Responded → Accepted

#### 3.4 Automated Reminders
Scheduled job runs daily:
- Day 3: gentle reminder to non-responders
- Day 5: "last chance" reminder
- Day 7: auto-close campaign, notify user

### Phase 4: Polish & Scale (Week 4+)

#### 4.1 Reverse Auction Mode
- Suppliers see lowest current bid (anonymized)
- Can update their bid until deadline
- Real-time updates via WebSocket
- Creates competitive pressure → lower prices

#### 4.2 SMS/WhatsApp Channel
- Many small Estonian contractors don't read email
- Twilio SMS: "Uus hinnapäring: Plaatimine 20m². Vasta siia: buildquote.eu/bid/xxx"
- WhatsApp Business API: richer messages with images

#### 4.3 Document Generation
- Auto-generate PDF quotation requests
- Auto-generate comparison reports
- Auto-generate acceptance/rejection letters
- Branded templates with BuildQuote design

#### 4.4 Supplier Portal
- Suppliers get their own login
- See incoming RFQs
- Manage their profile/categories
- View won/lost history
- Rate the buyer (two-way trust)

---

## 5. API Endpoints (Target State)

### Project Management
```
POST   /api/projects/parse          -- Parse text/file into project stages
POST   /api/projects                -- Create project from parsed result
GET    /api/projects                -- List user's projects (paginated)
GET    /api/projects/{id}           -- Get project with all stages
PUT    /api/projects/{id}           -- Update project
DELETE /api/projects/{id}           -- Delete project

GET    /api/projects/{id}/stages    -- Get all stages
PUT    /api/stages/{id}             -- Update stage
POST   /api/stages/{id}/requirements -- Generate AI requirements for stage
```

### RFQ Campaign Management  
```
POST   /api/campaigns               -- Create RFQ campaign for a stage
POST   /api/campaigns/{id}/send     -- Send RFQs to matched suppliers
GET    /api/campaigns/{id}          -- Campaign status + stats
GET    /api/campaigns/{id}/bids     -- All bids received
POST   /api/campaigns/{id}/remind   -- Send reminders to non-responders
POST   /api/campaigns/{id}/close    -- Close campaign

POST   /api/campaigns/{id}/accept-bid/{bidId}   -- Accept a bid
POST   /api/campaigns/{id}/decline-bid/{bidId}  -- Decline a bid
```

### Bid Management (public, no auth)
```
GET    /bid/{token}                 -- Bid response page (public)
POST   /api/bids/submit/{token}    -- Submit bid (public)
GET    /api/bids/{token}/status     -- Check if already submitted
```

### Supplier Management
```
GET    /api/suppliers/search        -- Unified search across all sources
GET    /api/suppliers/{id}          -- Supplier detail
GET    /api/companies               -- Paginated list of all companies
POST   /api/suppliers/match         -- AI-matched suppliers for a stage

POST   /api/onboard/send-invite     -- Send onboarding invite
POST   /api/onboard/register        -- Supplier self-registration
GET    /onboard/{token}             -- Onboarding page (public)
```

### Price Intelligence
```
POST   /api/prices/check            -- Get market price for category+area+location
POST   /api/prices/analyze-bid      -- Analyze bid vs market
GET    /api/prices/trends           -- Price trends over time
```

### Batch Operations
```
POST   /api/batch/harvest-suppliers  -- Run Google Places batch harvest
POST   /api/batch/import-fie        -- Import FIE data from avaandmed.rik.ee
GET    /api/batch/status             -- Background job status
```

---

## 6. Frontend Pages (Target State)

```
/                           -- Landing page (public)
/register                   -- Registration (public)
/login                      -- Login (public)
/bid/{token}                -- Bid submission (public, no auth)
/onboard/{token}            -- Supplier onboarding (public, no auth)

/dashboard                  -- Overview: stats, recent activity, price trends
/projects                   -- Project list (paginated)
/projects/new               -- Create project (text input OR file upload)
/projects/{id}              -- Project detail: stages, progress, costs
/projects/{id}/stages/{id}  -- Stage detail: RFQ status, bids, comparison

/companies                  -- All companies (paginated, searchable)
/suppliers                  -- User's saved/preferred suppliers
/campaigns                  -- All RFQ campaigns
/campaigns/{id}             -- Campaign detail: sent, opened, responded, bids

/wizard                     -- Quick single-job flow (simplified)
/settings                   -- User profile, email preferences, API keys
```

---

## 7. Tech Stack Decisions

| Layer | Technology | Reason |
|-------|-----------|--------|
| Frontend | Angular 19 (standalone) | Already built, SSR-ready |
| Backend | Spring Boot 3.x | Already built, enterprise-grade |
| Database | PostgreSQL 16 | Already running, JSONB for flexible data |
| AI Parsing | Anthropic Claude Sonnet | Best for structured extraction, Estonian support |
| AI Vision | Anthropic Claude Sonnet | Built-in multimodal |
| Email | Resend API | Already configured, 3000/mo free |
| Supplier Data | Google Places API | Already working, $200/mo free credit |
| File Parsing | Apache PDFBox + Apache POI | Java-native, no external deps |
| Cache | In-memory (ConcurrentHashMap) | Simple, sufficient for single-server |
| Queue | In-memory (LinkedBlockingQueue) | Email send queue, upgrade to Redis later |
| Deployment | Docker Compose | Already working on 37.60.225.35 |
| CI/CD | Manual (Claude Code) | Upgrade to GitHub Actions later |

---

## 8. Immediate Next Steps (Priority Order)

### Step 1: AI Project Parser (THE critical feature)
This is what Aleksei described as the "shock effect" — paste a project, AI breaks it down, 300 suppliers found.

### Step 2: Paginated Companies Page  
Aleksei requested this specifically. Simple but impressive.

### Step 3: End-to-End RFQ Verification
Verify that: wizard → select suppliers → send RFQ → email arrives → /bid/{token} works → bid stored → user sees it. This must work before showing anyone.

### Step 4: Batch Supplier Harvesting
Scale from 200 to 1000+ companies. Run Google Places across all Estonian cities.

### Step 5: Bug Fixes from Test Report
Logout, chunk loading, validation, mixed languages — all the issues from the QA report.

---

## 9. Claude Code Implementation Prompts

Below are the exact prompts to give Claude Code, in order.

### Prompt 1: AI Project Parser (Backend)
```
Do not make a plan. Build the AI Project Parser backend. 1) Add Anthropic 
Java SDK dependency to build.gradle: implementation 'com.anthropic:sdk:1.0.0' 
or use REST API with RestTemplate. 2) Create ProjectParserService that takes 
text input, calls Claude Sonnet API with a prompt that extracts construction 
stages (name, category, quantity, unit, description, dependencies). 3) Create 
ProjectParserController with POST /api/projects/parse accepting {description: 
"text"} and returning parsed stages with price estimates from market_prices 
table. 4) For each stage, also call /api/suppliers/search to count available 
suppliers. 5) Support file upload: POST /api/projects/parse-file accepting 
multipart PDF/DOCX, extract text with PDFBox/POI, then parse. 6) Add 
ANTHROPIC_API_KEY to application.properties. 7) Test with curl: parse "3 toa 
korteri remont Tallinnas, vannitoa plaatimine 20m2, elektri uuendamine 65m2, 
seinte viimistlus 180m2". 8) Deploy.
```

### Prompt 2: AI Project Parser (Frontend)
```
Do not make a plan. Build the project parser frontend page at /projects/new. 
1) Big textarea: "Kirjelda oma projekti" with placeholder example text. 2) 
File upload button for PDF/DOCX. 3) "Analüüsi projekti" button with loading 
animation. 4) Results: each stage as expandable card showing stage name, 
estimated area, price bar "Turuhind: €X - €Y", supplier count "Leitud N 
tegijat". 5) Checkbox per stage to include in mass RFQ. 6) Bottom summary: 
total project estimate + total suppliers + "Saada hinnapäring kõigile" 
button. 7) Dark theme, purple accents, translate pipe. 8) Deploy.
```

### Prompt 3: Paginated Companies Page
```
Do not make a plan. Create /companies page showing ALL companies paginated. 
1) Backend: GET /api/companies?page=0&size=25&search=&sort=name returns 
paginated results from ALL tables merged. 2) Frontend: table with columns 
Name, Contact, Phone, Categories (tags), Location, Source (colored badge). 
3) Search bar, column sorting, pagination. 4) Total count header. 5) Add 
"Ettevõtted" to sidebar. 6) Deploy.
```

### Prompt 4: End-to-End RFQ Verification
```
Do not make a plan. Test and fix the full RFQ email flow: 1) Go to wizard, 
fill Step 1, select suppliers in Step 2, click "Saada hinnapäring" in Step 3. 
2) Verify email arrives at kristo.erte@gmail.com (check Resend API key, 
spring-mail config). 3) Verify the email contains a link to /bid/{token}. 
4) Open /bid/{token} page — verify it shows job details and bid form. 5) 
Submit a test bid. 6) Verify bid appears in dashboard. 7) Fix anything 
broken. 8) Deploy.
```

### Prompt 5: Batch Supplier Harvest
```
Do not make a plan. Create a batch job to harvest suppliers from Google 
Places across all Estonian cities. 1) Create POST /api/batch/harvest endpoint.
2) Search 12 categories × 10 cities (Tallinn, Tartu, Pärnu, Narva, Rakvere, 
Viljandi, Kuressaare, Haapsalu, Jõhvi, Võru). 3) Rate limit 1 req/sec. 
4) Deduplicate by google_place_id. 5) Save to suppliers_unified table. 
6) Log progress and final count. 7) Run the harvest. 8) Deploy.
```
