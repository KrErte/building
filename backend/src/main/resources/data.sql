-- Seed market prices data (H2 compatible)
INSERT INTO market_prices (id, category, subcategory, unit, min_price, max_price, median_price, avg_price, sample_count, region, source, last_updated)
VALUES
    (RANDOM_UUID(), 'TILING', 'Bathroom tiling', 'm2', 25.00, 45.00, 35.00, 35.00, 50, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'TILING', 'Kitchen tiling', 'm2', 22.00, 40.00, 30.00, 31.00, 45, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'ELECTRICAL', 'Full rewiring', 'm2', 15.00, 30.00, 22.00, 22.50, 60, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'ELECTRICAL', 'Partial update', 'm2', 10.00, 20.00, 15.00, 15.00, 40, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'PLUMBING', 'Bathroom plumbing', 'm2', 20.00, 40.00, 30.00, 30.00, 55, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'PLUMBING', 'Kitchen plumbing', 'm2', 18.00, 35.00, 25.00, 26.00, 35, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'FINISHING', 'Wall painting', 'm2', 8.00, 15.00, 11.00, 11.50, 80, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'FINISHING', 'Ceiling work', 'm2', 10.00, 18.00, 14.00, 14.00, 45, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'FLOORING', 'Laminate', 'm2', 12.00, 25.00, 18.00, 18.50, 70, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'FLOORING', 'Parquet', 'm2', 20.00, 40.00, 30.00, 30.00, 40, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'DEMOLITION', 'General demolition', 'm2', 10.00, 20.00, 15.00, 15.00, 30, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'ROOFING', 'Roof repair', 'm2', 30.00, 60.00, 45.00, 45.00, 25, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'HVAC', 'Ventilation', 'm2', 25.00, 50.00, 37.00, 37.50, 35, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'WINDOWS_DOORS', 'Window installation', 'tk', 150.00, 350.00, 250.00, 250.00, 50, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'WINDOWS_DOORS', 'Door installation', 'tk', 80.00, 200.00, 140.00, 140.00, 45, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'GENERAL_CONSTRUCTION', 'General work', 'm2', 50.00, 120.00, 85.00, 85.00, 60, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'FACADE', 'Facade work', 'm2', 40.00, 80.00, 60.00, 60.00, 30, 'TALLINN', 'SEED', NOW()),
    (RANDOM_UUID(), 'LANDSCAPING', 'Landscaping', 'm2', 15.00, 35.00, 25.00, 25.00, 25, 'TALLINN', 'SEED', NOW());

-- Seed some sample suppliers
INSERT INTO suppliers_unified (id, company_name, email, phone, categories, service_areas, source, google_rating, trust_score, is_verified, created_at, updated_at)
VALUES
    (RANDOM_UUID(), 'Plaatija OÜ', 'info@plaatija.ee', '+372 5123 4567', ARRAY['TILING'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.5, 85, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Elekter Pro OÜ', 'elekter@elekterpro.ee', '+372 5234 5678', ARRAY['ELECTRICAL'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.8, 92, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Torumees AS', 'info@torumees.ee', '+372 5345 6789', ARRAY['PLUMBING'], ARRAY['TALLINN', 'HARJUMAA', 'TARTUMAA'], 'SEED', 4.2, 78, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Viimistlus Meister OÜ', 'meister@viimistlus.ee', '+372 5456 7890', ARRAY['FINISHING'], ARRAY['TALLINN'], 'SEED', 4.6, 88, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Põrandad OÜ', 'info@porandad.ee', '+372 5567 8901', ARRAY['FLOORING'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.4, 82, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Lammutus Grupp OÜ', 'lammutus@grupp.ee', '+372 5678 9012', ARRAY['DEMOLITION'], ARRAY['TALLINN', 'HARJUMAA', 'TARTUMAA'], 'SEED', 4.0, 75, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Katuse Tegijad AS', 'info@katused.ee', '+372 5789 0123', ARRAY['ROOFING'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.7, 90, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Ventilatsioon Expert OÜ', 'vent@expert.ee', '+372 5890 1234', ARRAY['HVAC'], ARRAY['TALLINN'], 'SEED', 4.3, 80, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Aknad ja Uksed OÜ', 'aknad@uksed.ee', '+372 5901 2345', ARRAY['WINDOWS_DOORS'], ARRAY['TALLINN', 'HARJUMAA', 'TARTUMAA'], 'SEED', 4.5, 86, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Ehitus Partner OÜ', 'info@ehituspartner.ee', '+372 5012 3456', ARRAY['GENERAL_CONSTRUCTION', 'DEMOLITION', 'FINISHING'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.6, 88, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Fassaad Meistrid AS', 'fassaad@meistrid.ee', '+372 5112 3456', ARRAY['FACADE'], ARRAY['TALLINN', 'HARJUMAA'], 'SEED', 4.4, 83, true, NOW(), NOW()),
    (RANDOM_UUID(), 'Aed ja Haljastus OÜ', 'aed@haljastus.ee', '+372 5212 3456', ARRAY['LANDSCAPING'], ARRAY['TALLINN', 'HARJUMAA', 'TARTUMAA'], 'SEED', 4.5, 85, true, NOW(), NOW());
