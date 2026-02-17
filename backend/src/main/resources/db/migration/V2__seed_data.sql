-- V2: Seed data for market prices and test users

-- Seed market prices for Estonian construction work
INSERT INTO market_prices (id, category, unit, min_price, max_price, median_price, avg_price, sample_count, region, source)
VALUES
    (gen_random_uuid(), 'TILING', 'm2', 25.00, 45.00, 35.00, 35.00, 50, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'TILING', 'm2', 22.00, 40.00, 30.00, 31.00, 30, 'Tartu', 'SEED'),
    (gen_random_uuid(), 'ELECTRICAL', 'm2', 15.00, 30.00, 22.00, 22.50, 45, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'ELECTRICAL', 'm2', 12.00, 25.00, 18.00, 18.50, 25, 'Tartu', 'SEED'),
    (gen_random_uuid(), 'PLUMBING', 'm2', 20.00, 40.00, 30.00, 30.00, 40, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'PLUMBING', 'm2', 18.00, 35.00, 26.00, 26.50, 20, 'Tartu', 'SEED'),
    (gen_random_uuid(), 'FINISHING', 'm2', 8.00, 18.00, 12.00, 13.00, 60, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'FINISHING', 'm2', 6.00, 15.00, 10.00, 10.50, 35, 'Tartu', 'SEED'),
    (gen_random_uuid(), 'FLOORING', 'm2', 12.00, 28.00, 18.00, 20.00, 55, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'FLOORING', 'm2', 10.00, 24.00, 16.00, 17.00, 30, 'Tartu', 'SEED'),
    (gen_random_uuid(), 'DEMOLITION', 'm2', 8.00, 20.00, 12.00, 14.00, 35, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'ROOFING', 'm2', 30.00, 65.00, 45.00, 47.50, 25, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'HVAC', 'm2', 25.00, 55.00, 38.00, 40.00, 20, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'WINDOWS_DOORS', 'tk', 150.00, 400.00, 250.00, 275.00, 40, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'FACADE', 'm2', 35.00, 80.00, 55.00, 57.50, 15, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'LANDSCAPING', 'm2', 15.00, 40.00, 25.00, 27.50, 20, 'Tallinn', 'SEED'),
    (gen_random_uuid(), 'GENERAL_CONSTRUCTION', 'm2', 20.00, 50.00, 35.00, 35.00, 100, 'Tallinn', 'SEED')
ON CONFLICT DO NOTHING;

-- Seed test suppliers
INSERT INTO suppliers (id, company_name, email, phone, city, categories, source, google_rating, google_review_count, trust_score, is_verified)
VALUES
    (gen_random_uuid(), 'Plaatija Peeter OÜ', 'peeter@plaatija.ee', '+372 5123 4567', 'Tallinn', 'TILING', 'SEED', 4.8, 45, 85, true),
    (gen_random_uuid(), 'Elektri Ekspert AS', 'info@elektriekspert.ee', '+372 5234 5678', 'Tallinn', 'ELECTRICAL', 'SEED', 4.6, 32, 80, true),
    (gen_random_uuid(), 'Torumees Toomas OÜ', 'toomas@torumees.ee', '+372 5345 6789', 'Tallinn', 'PLUMBING', 'SEED', 4.5, 28, 75, true),
    (gen_random_uuid(), 'Viimistlus Meister OÜ', 'meister@viimistlus.ee', '+372 5456 7890', 'Tallinn', 'FINISHING', 'SEED', 4.7, 52, 82, true),
    (gen_random_uuid(), 'Põrandaparadijs OÜ', 'info@porandaparadijs.ee', '+372 5567 8901', 'Tallinn', 'FLOORING', 'SEED', 4.4, 19, 70, false),
    (gen_random_uuid(), 'Ehitusgrupp Tartu AS', 'tartu@ehitusgrupp.ee', '+372 5678 9012', 'Tartu', 'GENERAL_CONSTRUCTION,DEMOLITION', 'SEED', 4.3, 67, 78, true),
    (gen_random_uuid(), 'Katus ja Fassaad OÜ', 'info@katusfassaad.ee', '+372 5789 0123', 'Tallinn', 'ROOFING,FACADE', 'SEED', 4.9, 89, 92, true),
    (gen_random_uuid(), 'Küte & Ventilatsioon AS', 'kontakt@kuteventilatsioon.ee', '+372 5890 1234', 'Tallinn', 'HVAC', 'SEED', 4.2, 15, 68, false),
    (gen_random_uuid(), 'Aknad-Uksed Pro OÜ', 'pro@aknaduksed.ee', '+372 5901 2345', 'Tallinn', 'WINDOWS_DOORS', 'SEED', 4.6, 41, 79, true),
    (gen_random_uuid(), 'Aiaehitus Meister OÜ', 'aed@aiaehitus.ee', '+372 5012 3456', 'Tallinn', 'LANDSCAPING', 'SEED', 4.5, 23, 72, false)
ON CONFLICT DO NOTHING;
