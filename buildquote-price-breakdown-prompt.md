# Claude Code Prompt: Price Breakdown Expandable Panel

Do not make a plan. Do not ask questions. Just implement.

## Context
BuildQuote is a construction procurement platform. Stack: Angular 19 (standalone components, TailwindCSS dark theme), Spring Boot 3.x, PostgreSQL. The app already has work stage cards (Lammutustööd, Vundamenditööd, Karkassitööd etc.) that show price ranges and supplier counts. Currently clicking a card shows only a description and a market price bar.

## Task
Add a detailed price breakdown accordion panel inside each work stage card. When user clicks a stage card, it expands to show HOW the price was calculated — materials, labor, sources.

## Backend

### 1. New DTO: `PriceBreakdownDTO.java`
```java
public record PriceBreakdownDTO(
    List<MaterialLineDTO> materials,
    LaborCostDTO labor,
    OtherCostsDTO otherCosts,
    int confidencePercent,
    String confidenceLabel,
    BigDecimal totalMin,
    BigDecimal totalMax
) {}

public record MaterialLineDTO(
    String name,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPriceMin,
    BigDecimal unitPriceMax,
    String supplierName,
    String supplierUrl,
    String priceSource,        // "AUTO" or "MANUAL"
    LocalDate lastUpdated
) {}

public record LaborCostDTO(
    BigDecimal hoursEstimate,
    BigDecimal hourlyRateMin,
    BigDecimal hourlyRateMax,
    BigDecimal totalMin,
    BigDecimal totalMax,
    String source
) {}

public record OtherCostsDTO(
    BigDecimal transportMin,
    BigDecimal transportMax,
    BigDecimal wasteDisposalMin,
    BigDecimal wasteDisposalMax,
    BigDecimal totalMin,
    BigDecimal totalMax
) {}
```

### 2. New endpoint
```
GET /api/projects/{projectId}/stages/{stageId}/price-breakdown
```
Returns `PriceBreakdownDTO`. Build a service that:
- Pulls material prices from the existing supplier/price database
- Calculates labor based on stage type + area (m2) using Estonian construction sector averages
- Calculates confidence % based on: how many prices are from real scraped data vs estimates, and how fresh the prices are (>30 days old = lower confidence)
- If no real price data exists, return estimated ranges with low confidence

### 3. Supplier price comparison endpoint
```
GET /api/materials/{materialName}/suppliers?region=estonia
```
Returns list of `{ supplierName, price, url, lastUpdated }` for the same material from different suppliers (Bauhof, Ehituse ABC, Decora, Espak etc.)

## Frontend

### 1. Price Breakdown Component
Create `price-breakdown.component.ts` (standalone, Angular 19).

Structure inside the expanded card (below existing description + market price bar):

```
┌─────────────────────────────────────────────────────┐
│ MATERJALID                                          │
│ ┌─────────┬───────┬──────┬──────────┬───────┬─────┐ │
│ │Materjal │Kogus  │Ühik  │Ühikuhind │Allikas│Kokku│ │
│ │Betoon   │45     │m³    │95€       │Rudus ▪│4275€│ │
│ │Armatuur │2800   │kg    │1.10€     │Merko ▪│3080€│ │
│ └─────────┴───────┴──────┴──────────┴───────┴─────┘ │
│ ▪ = clickable supplier link                         │
│ Each row: badge "Automaatne" (green) or "Käsitsi"   │
│ Each row: "Uuendatud: 14.02.2025" in gray small text│
│                                                      │
│ TÖÖJÕUD                                             │
│ ~120h × 25-35 €/h = 3 000 € — 4 200 €              │
│ Allikas: Eesti ehitussektori keskmine 2024-2025      │
│                                                      │
│ KOKKUVÕTE                          TÄPSUS: 72% 🟡   │
│ ┌──────────────────────────────────────────┐         │
│ │ Materjalid:    7 355 € — 9 200 €        │         │
│ │ Tööjõud:       3 000 € — 4 200 €        │         │
│ │ Muud kulud:    1 200 € — 1 500 €        │         │
│ │ ──────────────────────────────           │         │
│ │ KOKKU:        11 555 € — 14 900 €       │         │
│ └──────────────────────────────────────────┘         │
│                                                      │
│ [Võrdle tarnijaid]  [Muuda koguseid]  [PDF eksport]  │
└─────────────────────────────────────────────────────┘
```

### 2. Confidence indicator
- `confidencePercent >= 80` → green dot + "Kõrge täpsus"
- `confidencePercent >= 50` → yellow dot + "Keskmine täpsus"
- `confidencePercent < 50` → red dot + "Madal täpsus — põhineb hinnangutel"

### 3. Supplier comparison modal
When user clicks "Võrdle tarnijaid" or clicks a supplier name in the table, open a modal/slide-over showing the same material from multiple suppliers with prices, sorted cheapest first.

### 4. Editable quantities
"Muuda koguseid" button toggles inline editing on the quantity column. When user changes a quantity, recalculate totals client-side immediately (no API call). Show a "Salvesta muudatused" button that PATCHes the updated quantities to backend.

### 5. UI requirements
- Dark theme (already in use), use existing Tailwind classes
- Smooth accordion animation (Angular @trigger or CSS transition, max-height approach)
- Lazy load: only fetch price breakdown data on first expand click
- Loading skeleton while data loads
- All text in Estonian
- Responsive: table becomes stacked cards on mobile
- Numbers formatted with space as thousands separator (12 500 €)

## Do NOT
- Do not change existing card layout or functionality
- Do not add new npm packages unless absolutely necessary
- Do not create separate CSS files — use Tailwind only
- Do not add authentication changes
