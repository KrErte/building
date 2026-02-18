package com.buildquote.service;

import com.buildquote.dto.DependentMaterialDto;
import com.buildquote.dto.MaterialSummaryDto;
import com.buildquote.dto.PipeComponentDto;
import com.buildquote.dto.PipeSystemDto;
import com.buildquote.dto.ProjectParseResult;
import com.buildquote.dto.ProjectStageDto;
import com.buildquote.entity.MarketPrice;
import com.buildquote.repository.MarketPriceRepository;
import com.buildquote.repository.SupplierRepository;
import com.buildquote.service.SupplierSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectParserService {

    private final AnthropicService anthropicService;
    private final IfcParserService ifcParserService;
    private final IfcProcessingService ifcProcessingService;
    private final IfcOpenShellParserService ifcOpenShellParserService;
    private final DxfParserService dxfParserService;
    private final MarketPriceRepository marketPriceRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierSearchService supplierSearchService;
    private final DependentMaterialService dependentMaterialService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService enrichmentExecutor = Executors.newFixedThreadPool(8);

    private static final String VISION_PROMPT = """
        You are a construction project analyzer specializing in reading construction drawings and plans.
        Analyze this image which shows a construction drawing, floor plan, or architectural plan.

        Extract ALL information you can identify:
        1. Room names and their approximate sizes (m²)
        2. Wall types and lengths
        3. Door and window positions and sizes
        4. Any dimensions marked on the drawing
        5. Material specifications if visible
        6. MEP (mechanical, electrical, plumbing) elements if shown
        7. Any text labels or annotations
        8. Scale of the drawing if indicated

        Based on this analysis, provide a detailed text description of the construction work needed.
        Include estimated quantities where possible (m², tk, jm).

        Response should be a comprehensive description in Estonian that can be used to estimate
        construction stages and costs. Focus on practical construction information.
        """;

    private static final String PARSE_PROMPT = """
        You are a construction project analyzer for Estonian construction projects.
        Given a project description, extract ALL construction work items as separate stages.

        CRITICAL RULES:
        - Keep each window, door, or material item as a SEPARATE stage (do NOT merge similar items)
        - If document lists "Aken A1 (5tk), Aken A2 (3tk)", create TWO separate stages with their original names
        - Preserve the original item codes/names/marks from the document (e.g., "A-1", "UK-2", "Aken W1")
        - Each unique product/item type = separate stage
        - Do NOT group windows together or doors together - keep them as listed in the source

        For each stage provide:
        - name: Original item name/code from document + Estonian description (e.g., "Aken A-1 paigaldus", "Uks UK-2 paigaldus")
        - category: one of [GENERAL_CONSTRUCTION, ELECTRICAL, PLUMBING, TILING, FINISHING, ROOFING, FACADE, LANDSCAPING, DEMOLITION, FLOORING, HVAC, WINDOWS_DOORS, OTHER]
        - quantity: exact amount as stated in document
        - unit: one of [m2, tk, jm, h]
        - description: specifications from document (dimensions, materials, features)
        - dependencies: list of stage names that must be done first (empty array if none)

        Also extract:
        - projectTitle: short title in Estonian
        - location: city/address if mentioned (default to "Tallinn" if not specified)
        - totalBudget: total budget as number if mentioned, null otherwise
        - deadline: deadline if mentioned, null otherwise

        Return ONLY valid JSON in this exact format:
        {
          "projectTitle": "string",
          "location": "string",
          "totalBudget": number or null,
          "deadline": "string or null",
          "stages": [
            {
              "name": "string",
              "category": "string",
              "quantity": number,
              "unit": "string",
              "description": "string",
              "dependencies": ["string"]
            }
          ]
        }

        Project description:
        """;

    private static final String PIPE_SYSTEM_PROMPT = """
        You are a specialist in reading plumbing, HVAC and MEP construction drawings.
        Analyze these construction drawing images and identify ALL pipe systems shown.

        Common Estonian pipe system codes:
        - SK = Sademeveekanalisatsioon (rainwater sewage)
        - OK = Olmekanalisatsioon (domestic sewage)
        - RV = Reoveekanalisatsioon (wastewater)
        - VV = Veevarustus (water supply - cold)
        - SV = Sooja vee varustus (hot water supply)
        - K = Küte (heating)
        - DR = Drenaaž (drainage)
        - TV = Tuletõrjeveevarustus (fire water supply)
        - V1/V2 = Ventilatsioon (ventilation ducts)

        For EACH pipe system found, identify:
        1. System code and full name
        2. System type (SEWAGE, RAINWATER, WATER_SUPPLY, HOT_WATER, VENTILATION, HEATING, DRAIN, FIRE_WATER)
        3. Total estimated pipe length in meters
        4. Pipe specifications (material, diameters used)
        5. Component counts: straight pipe sections, elbows (45° and 90°), tees, couplings, reducers, valves, manholes, drains, cleanouts
        6. Your confidence level (0.0-1.0) for the system detection and for each component

        Rules:
        - If you see direction changes in pipes, infer elbows even if symbols aren't clearly visible
        - Count each fitting individually
        - For straight pipe, estimate total length in meters per diameter
        - If diameter is not clearly marked, infer from context (domestic sewage typically DN110, rainwater DN110-160, water supply DN25-50)
        - Only include systems you can actually identify in the drawing
        - Set confidence lower for inferred components vs clearly visible ones

        Return ONLY valid JSON in this exact format:
        {
          "pipeSystems": [
            {
              "systemCode": "SK",
              "systemName": "Sademeveekanalisatsioon",
              "systemType": "RAINWATER",
              "lengthMeters": 45.5,
              "pipeSpecs": "PP DN110-160",
              "confidence": 0.85,
              "components": [
                {
                  "type": "STRAIGHT",
                  "typeLabel": "Sirge toru",
                  "diameterMm": 110,
                  "count": 1,
                  "lengthM": 30.0,
                  "material": "PP",
                  "confidence": 0.8
                },
                {
                  "type": "ELBOW_90",
                  "typeLabel": "Põlv 90°",
                  "diameterMm": 110,
                  "count": 6,
                  "lengthM": null,
                  "material": "PP",
                  "confidence": 0.7
                }
              ]
            }
          ]
        }
        """;

    // Thread-local storage for page images rendered during PDF extraction (reused for pipe detection)
    private final ThreadLocal<List<byte[]>> lastPageImages = new ThreadLocal<>();
    private final ThreadLocal<List<String>> lastMediaTypes = new ThreadLocal<>();

    public ProjectParseResult parseFromText(String description) {
        log.info("Parsing project description (no prices): {}", description.substring(0, Math.min(100, description.length())));

        String prompt = PARSE_PROMPT + description;
        String response = anthropicService.callClaude(prompt);

        if (response == null) {
            log.error("Failed to get response from Claude API");
            return createFallbackResult(description);
        }

        try {
            // Extract JSON from response (Claude might add text around it)
            String jsonStr = extractJson(response);
            JsonNode root = objectMapper.readTree(jsonStr);

            ProjectParseResult result = new ProjectParseResult();
            result.setProjectTitle(root.path("projectTitle").asText("Ehitusprojekt"));
            result.setLocation(root.path("location").asText("Tallinn"));

            if (!root.path("totalBudget").isNull()) {
                result.setTotalBudget(new BigDecimal(root.path("totalBudget").asText("0")));
            }
            result.setDeadline(root.path("deadline").asText(null));

            List<ProjectStageDto> stages = new ArrayList<>();
            JsonNode stagesNode = root.path("stages");

            if (stagesNode.isArray()) {
                for (JsonNode stageNode : stagesNode) {
                    ProjectStageDto stage = new ProjectStageDto();
                    stage.setName(stageNode.path("name").asText());
                    stage.setCategory(stageNode.path("category").asText());
                    stage.setQuantity(new BigDecimal(stageNode.path("quantity").asText("0")));
                    stage.setUnit(stageNode.path("unit").asText("m2"));
                    stage.setDescription(stageNode.path("description").asText());

                    List<String> deps = new ArrayList<>();
                    JsonNode depsNode = stageNode.path("dependencies");
                    if (depsNode.isArray()) {
                        for (JsonNode dep : depsNode) {
                            deps.add(dep.asText());
                        }
                    }
                    stage.setDependencies(deps);
                    stages.add(stage);
                }
            }

            result.setStages(stages);
            return result;
        } catch (Exception e) {
            log.error("Error parsing Claude response: {}", e.getMessage(), e);
            return createFallbackResult(description);
        }
    }

    /**
     * Enrich stages with market prices and supplier counts.
     * Called separately after user confirms quantities.
     */
    public ProjectParseResult enrichWithPrices(ProjectParseResult result) {
        String location = result.getLocation();
        List<CompletableFuture<Void>> enrichmentFutures = new ArrayList<>();

        for (ProjectStageDto stage : result.getStages()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                enrichWithMarketPrices(stage, location);
                enrichWithSupplierCount(stage, location);
            }, enrichmentExecutor);
            enrichmentFutures.add(future);
        }

        try {
            CompletableFuture.allOf(enrichmentFutures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Parallel enrichment timeout, some stages may have default values");
        }

        // Enrich pipe system component prices
        if (result.getPipeSystems() != null && !result.getPipeSystems().isEmpty()) {
            enrichPipeSystemPrices(result.getPipeSystems());
        }

        // Calculate dependent materials from component recipes
        try {
            List<DependentMaterialDto> dependentMaterials =
                dependentMaterialService.calculateDependentMaterials(result.getStages());
            result.setDependentMaterials(dependentMaterials);
        } catch (Exception e) {
            log.warn("Dependent material calculation failed: {}", e.getMessage());
        }

        calculateTotals(result);
        buildSummary(result);
        return result;
    }

    public ProjectParseResult parseFromFile(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            filename = "unknown";
        }
        String lowerFilename = filename.toLowerCase();
        String text;

        log.info("Processing file: {} (type: {}, size: {} bytes)", filename, file.getContentType(), file.getSize());

        // Handle different file types
        if (lowerFilename.endsWith(".pdf")) {
            text = extractFromPdf(file);

            // After PDF parse, check if we stored page images and if the result contains MEP stages
            ProjectParseResult result = parseFromText(text);
            List<byte[]> storedImages = lastPageImages.get();
            List<String> storedTypes = lastMediaTypes.get();
            try {
                if (storedImages != null && !storedImages.isEmpty() && hasMepStages(result)) {
                    log.info("MEP stages detected, running pipe system detection on {} page images", storedImages.size());
                    List<PipeSystemDto> pipeSystems = detectPipeSystems(storedImages, storedTypes);
                    result.setPipeSystems(pipeSystems);
                }
            } finally {
                lastPageImages.remove();
                lastMediaTypes.remove();
            }
            return result;
        } else if (lowerFilename.endsWith(".docx") || lowerFilename.endsWith(".doc")) {
            text = extractTextFromDocx(file.getInputStream());
        } else if (lowerFilename.endsWith(".zip")) {
            // ZIP archives containing multiple file types (IFC, DWG, DXF, RVT, PDF, images)
            text = extractFromZip(file);
        } else if (lowerFilename.endsWith(".ifc")) {
            // IFC BIM files
            text = extractFromIfc(file);
        } else if (lowerFilename.endsWith(".dxf")) {
            text = extractFromDxf(file);
        } else if (lowerFilename.endsWith(".dwg")) {
            // DWG is binary - suggest alternatives or try to analyze file header
            text = handleDwgFile(file);
        } else if (lowerFilename.endsWith(".rvt")) {
            // Revit files are binary - provide guidance
            text = handleRevitFile(file);
        } else if (isImageFile(lowerFilename)) {
            return parseFromImage(file);
        } else if (lowerFilename.endsWith(".txt")) {
            text = new String(file.getBytes());
        } else {
            // Try to read as text
            text = new String(file.getBytes());
        }

        return parseFromText(text);
    }

    private boolean isImageFile(String filename) {
        return filename.endsWith(".jpg") || filename.endsWith(".jpeg") ||
               filename.endsWith(".png") || filename.endsWith(".gif") ||
               filename.endsWith(".bmp") || filename.endsWith(".webp");
    }

    /**
     * Extract from PDF - try text first, then use Vision API for scanned/image PDFs
     */
    private String extractFromPdf(MultipartFile file) throws IOException {
        byte[] pdfBytes = file.getBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // If PDF has substantial text, use it
            if (text != null && text.trim().length() > 100) {
                log.info("PDF has {} chars of text content", text.length());
                return text;
            }

            // PDF might be scanned/image-based - render pages and use Vision API
            log.info("PDF appears to be image-based, using Vision API");
            return extractFromPdfWithVision(document);
        }
    }

    /**
     * Render PDF pages to images and analyze with Vision API
     */
    private String extractFromPdfWithVision(PDDocument document) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        List<byte[]> pageImages = new ArrayList<>();
        List<String> mediaTypes = new ArrayList<>();

        // Render up to 5 pages (API limit considerations)
        int pageCount = Math.min(document.getNumberOfPages(), 5);

        for (int i = 0; i < pageCount; i++) {
            BufferedImage image = renderer.renderImageWithDPI(i, 150);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            pageImages.add(baos.toByteArray());
            mediaTypes.add("image/png");
        }

        // Store rendered images for potential pipe system detection reuse
        lastPageImages.set(pageImages);
        lastMediaTypes.set(mediaTypes);

        String visionResponse;
        if (pageImages.size() == 1) {
            visionResponse = anthropicService.callClaudeVision(
                pageImages.get(0), "image/png", VISION_PROMPT
            );
        } else {
            visionResponse = anthropicService.callClaudeVisionMultiple(
                pageImages, mediaTypes,
                VISION_PROMPT + "\n\nThis document has " + pageCount + " pages. Analyze all pages together."
            );
        }

        if (visionResponse != null) {
            return "Ehitusplaanilt tuvastatud (Vision AI):\n\n" + visionResponse;
        }

        return "PDF-faili ei õnnestunud analüüsida. Palun laadige üles selgem pilt või kirjeldage projekti tekstina.";
    }

    /**
     * Parse image file using Vision API
     */
    private ProjectParseResult parseFromImage(MultipartFile file) throws IOException {
        log.info("Processing image file with Vision API: {}", file.getOriginalFilename());

        byte[] imageBytes = file.getBytes();
        String mediaType = getMediaType(file.getOriginalFilename());

        String visionResponse = anthropicService.callClaudeVision(imageBytes, mediaType, VISION_PROMPT);

        if (visionResponse != null) {
            String description = "Ehitusplaanilt/fotolt tuvastatud (Vision AI):\n\n" + visionResponse;
            ProjectParseResult result = parseFromText(description);

            // If MEP stages detected, run pipe system detection on the uploaded image
            if (hasMepStages(result)) {
                log.info("MEP stages detected in image, running pipe system detection");
                List<byte[]> images = List.of(imageBytes);
                List<String> types = List.of(mediaType);
                List<PipeSystemDto> pipeSystems = detectPipeSystems(images, types);
                result.setPipeSystems(pipeSystems);
            }

            return result;
        }

        // Fallback if vision fails
        return createFallbackResult("Pildifaili analüüs");
    }

    private String getMediaType(String filename) {
        if (filename == null) return "image/jpeg";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /**
     * Extract from IFC/BIM file or ZIP containing IFC.
     * Uses IfcOpenShell Python parser for detailed extraction.
     */
    private String extractFromIfc(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        log.info("Processing IFC/BIM file: {} ({} KB)", filename, file.getSize() / 1024);

        try {
            // Use the new IfcOpenShell-based parser (supports both .ifc and .zip)
            var ifcData = ifcProcessingService.processIfcFileSync(file);

            // Convert structured data to text description for Claude
            return buildIfcDescription(ifcData, filename);

        } catch (Exception e) {
            log.warn("IfcOpenShell parser failed, falling back to basic parser: {}", e.getMessage());

            // Fallback to basic regex parser for .ifc files only
            if (filename != null && filename.toLowerCase().endsWith(".ifc")) {
                IfcParserService.IfcParseResult ifcResult = ifcParserService.parseIfc(file.getInputStream());
                return ifcResult.getDescription();
            }

            throw new IOException("IFC faili töötlemine ebaõnnestus: " + e.getMessage(), e);
        }
    }

    /**
     * Build a detailed text description from parsed IFC data for Claude to analyze.
     */
    private String buildIfcDescription(com.buildquote.dto.ifc.IfcBuildingData data, String filename) {
        StringBuilder sb = new StringBuilder();

        sb.append("BIM/IFC mudelist ekstraktitud ehitusinfo:\n\n");

        // File info
        if (data.fileInfo() != null) {
            sb.append("FAILI INFO:\n");
            sb.append("- Skeem: ").append(data.fileInfo().schemaVersion()).append("\n");
            if (data.fileInfo().originatingSystem() != null && !data.fileInfo().originatingSystem().isBlank()) {
                sb.append("- Loodud: ").append(data.fileInfo().originatingSystem()).append("\n");
            }
            sb.append("\n");
        }

        // Project info
        if (data.project() != null && data.project().name() != null) {
            sb.append("PROJEKT: ").append(data.project().name()).append("\n\n");
        }

        // Building structure
        if (data.spatialStructure() != null) {
            if (data.spatialStructure().buildings() != null && !data.spatialStructure().buildings().isEmpty()) {
                sb.append("HOONED:\n");
                for (var building : data.spatialStructure().buildings()) {
                    sb.append("- ").append(building.name() != null ? building.name() : "Hoone").append("\n");
                    if (building.address() != null && !building.address().isBlank()) {
                        sb.append("  Aadress: ").append(building.address()).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (data.spatialStructure().storeys() != null && !data.spatialStructure().storeys().isEmpty()) {
                sb.append("KORRUSED: ").append(data.spatialStructure().storeys().size()).append(" tk\n");
                for (var storey : data.spatialStructure().storeys()) {
                    sb.append("- ").append(storey.name() != null ? storey.name() : "Korrus");
                    sb.append(" (kõrgus: ").append(storey.elevation()).append("m)\n");
                }
                sb.append("\n");
            }
        }

        // Spaces/rooms
        if (data.spaces() != null && !data.spaces().isEmpty()) {
            sb.append("RUUMID: ").append(data.spaces().size()).append(" tk\n");
            double totalArea = 0;
            for (var space : data.spaces()) {
                sb.append("- ").append(space.name() != null && !space.name().isBlank() ? space.name() : "Ruum");
                if (space.area() > 0) {
                    sb.append(": ").append(String.format("%.1f", space.area())).append(" m²");
                    totalArea += space.area();
                }
                sb.append("\n");
            }
            if (totalArea > 0) {
                sb.append("Kokku pindala: ").append(String.format("%.1f", totalArea)).append(" m²\n");
            }
            sb.append("\n");
        }

        // Quantity summary
        if (data.quantitySummary() != null) {
            var qs = data.quantitySummary();

            sb.append("EHITUSELEMENDID:\n");
            if (qs.wallCount() > 0) sb.append("- Seinad: ").append(qs.wallCount()).append(" tk");
            if (qs.totalWallArea() > 0) sb.append(", ~").append(String.format("%.1f", qs.totalWallArea())).append(" m²");
            if (qs.wallCount() > 0) sb.append("\n");

            if (qs.slabCount() > 0) sb.append("- Plaadid/põrandad: ").append(qs.slabCount()).append(" tk");
            if (qs.totalSlabArea() > 0) sb.append(", ~").append(String.format("%.1f", qs.totalSlabArea())).append(" m²");
            if (qs.slabCount() > 0) sb.append("\n");

            if (qs.columnCount() > 0) sb.append("- Sambad: ").append(qs.columnCount()).append(" tk\n");
            if (qs.beamCount() > 0) sb.append("- Talad: ").append(qs.beamCount()).append(" tk\n");
            if (qs.doorCount() > 0) sb.append("- Uksed: ").append(qs.doorCount()).append(" tk\n");
            if (qs.windowCount() > 0) sb.append("- Aknad: ").append(qs.windowCount()).append(" tk\n");
            sb.append("\n");

            // MEP elements
            if (qs.totalMepElements() > 0) {
                sb.append("TEHNOSÜSTEEMID (KVVK):\n");
                if (qs.pipeSegmentCount() > 0) {
                    sb.append("- Torusegmendid: ").append(qs.pipeSegmentCount()).append(" tk");
                    if (qs.totalPipeLength() > 0) sb.append(", ~").append(String.format("%.1f", qs.totalPipeLength())).append(" m");
                    sb.append("\n");
                }
                if (qs.pipeFittingCount() > 0) sb.append("- Toruliitmikud: ").append(qs.pipeFittingCount()).append(" tk\n");
                if (qs.ductSegmentCount() > 0) {
                    sb.append("- Ventilatsiooni kanalid: ").append(qs.ductSegmentCount()).append(" tk");
                    if (qs.totalDuctLength() > 0) sb.append(", ~").append(String.format("%.1f", qs.totalDuctLength())).append(" m");
                    sb.append("\n");
                }
                if (qs.ductFittingCount() > 0) sb.append("- Kanali liitmikud: ").append(qs.ductFittingCount()).append(" tk\n");
                if (qs.valveCount() > 0) sb.append("- Klapid/ventiilid: ").append(qs.valveCount()).append(" tk\n");
                if (qs.pumpCount() > 0) sb.append("- Pumbad: ").append(qs.pumpCount()).append(" tk\n");
                if (qs.boilerCount() > 0) sb.append("- Katlad: ").append(qs.boilerCount()).append(" tk\n");
                if (qs.fanCount() > 0) sb.append("- Ventilaatorid: ").append(qs.fanCount()).append(" tk\n");
                if (qs.flowTerminalCount() > 0) sb.append("- Terminaalid (radiaatorid, õhupääsud jne): ").append(qs.flowTerminalCount()).append(" tk\n");
                sb.append("\n");

                // By system type
                if (qs.elementCountBySystem() != null && !qs.elementCountBySystem().isEmpty()) {
                    sb.append("SÜSTEEMID:\n");
                    for (var entry : qs.elementCountBySystem().entrySet()) {
                        String systemName = switch (entry.getKey()) {
                            case "heating" -> "Küte";
                            case "water_supply" -> "Veevarustus";
                            case "sewage" -> "Kanalisatsioon";
                            case "ventilation" -> "Ventilatsioon";
                            case "fire_suppression" -> "Tulekustutus";
                            default -> entry.getKey();
                        };
                        sb.append("- ").append(systemName).append(": ").append(entry.getValue()).append(" elementi\n");
                    }
                    sb.append("\n");
                }
            }
        }

        // Materials
        if (data.materials() != null && !data.materials().isEmpty()) {
            sb.append("MATERJALID:\n");
            int shown = 0;
            for (var mat : data.materials()) {
                if (shown >= 15) {
                    sb.append("- ... ja ").append(data.materials().size() - 15).append(" materjali veel\n");
                    break;
                }
                sb.append("- ").append(mat.name()).append(" (").append(mat.usageCount()).append(" elementi)\n");
                shown++;
            }
            sb.append("\n");
        }

        // Summary
        if (data.quantitySummary() != null) {
            sb.append("KOKKUVÕTE:\n");
            sb.append("- Ehituselemente: ").append(data.quantitySummary().totalElements()).append(" tk\n");
            sb.append("- Tehnosüsteemide elemente: ").append(data.quantitySummary().totalMepElements()).append(" tk\n");
            if (data.parseTimeMs() > 0) {
                sb.append("- Parsimise aeg: ").append(data.parseTimeMs()).append(" ms\n");
            }
        }

        return sb.toString();
    }

    /**
     * Extract from ZIP archive containing multiple file types.
     * Supports: IFC, DWG, DXF, RVT, PDF, and image files.
     * OPTIMIZED: Parallel processing, limited files, fast timeouts.
     */
    private String extractFromZip(MultipartFile file) throws IOException {
        String zipFilename = file.getOriginalFilename();
        log.info("Processing ZIP archive: {} ({} KB)", zipFilename, file.getSize() / 1024);

        Path tempDir = Files.createTempDirectory("zip_extract_" + UUID.randomUUID().toString().substring(0, 8));
        List<Path> extractedFiles = new ArrayList<>();
        List<String> extractedNames = new ArrayList<>();

        // STEP 1: Extract all files first (fast)
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            int fileCount = 0;
            int maxFiles = 15; // Limit to 15 files for speed

            while ((entry = zis.getNextEntry()) != null && fileCount < maxFiles) {
                String entryName = entry.getName();
                if (entry.isDirectory()) continue;

                String normalizedName = Path.of(entryName).getFileName().toString();
                if (normalizedName.startsWith(".") || normalizedName.startsWith("__MACOSX")) continue;

                String lowerName = normalizedName.toLowerCase();
                if (!isSupportedZipEntry(lowerName)) {
                    zis.closeEntry();
                    continue;
                }

                // Prioritize IFC files (most valuable)
                String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + normalizedName;
                Path extractedFile = tempDir.resolve(uniqueName);
                Files.copy(zis, extractedFile, StandardCopyOption.REPLACE_EXISTING);

                extractedFiles.add(extractedFile);
                extractedNames.add(normalizedName);
                fileCount++;

                log.info("Extracted from ZIP: {} ({}/{})", normalizedName, fileCount, maxFiles);
                zis.closeEntry();
            }
        }

        if (extractedFiles.isEmpty()) {
            cleanupDirectory(tempDir);
            return "ZIP-arhiivist ei leitud ühtegi toetatud faili.";
        }

        // STEP 2: Process files IN PARALLEL
        log.info("Processing {} files in parallel...", extractedFiles.size());
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 0; i < extractedFiles.size(); i++) {
            final Path filePath = extractedFiles.get(i);
            final String fileName = extractedNames.get(i);

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String description = processExtractedFileFast(filePath, fileName);
                    if (description != null && !description.isBlank()) {
                        return "=== " + fileName + " ===\n" + description;
                    }
                } catch (Exception e) {
                    log.warn("Failed to process {}: {}", fileName, e.getMessage());
                }
                return null;
            }, enrichmentExecutor);

            futures.add(future);
        }

        // Wait for all with timeout (max 30 seconds total)
        List<String> results = new ArrayList<>();
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

            for (CompletableFuture<String> f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    String result = f.getNow(null);
                    if (result != null) results.add(result);
                }
            }
        } catch (Exception e) {
            log.warn("Parallel processing timeout, using partial results");
            for (CompletableFuture<String> f : futures) {
                if (f.isDone() && !f.isCompletedExceptionally()) {
                    String result = f.getNow(null);
                    if (result != null) results.add(result);
                }
            }
        }

        // Cleanup
        cleanupDirectory(tempDir);

        if (results.isEmpty()) {
            return "ZIP-arhiivi failide töötlemine ebaõnnestus.";
        }

        log.info("ZIP processing complete: {} files processed", results.size());
        return "ZIP-arhiivist ekstraktitud (" + results.size() + " faili):\n\n" +
               String.join("\n\n", results);
    }

    /**
     * Fast file processing - skip slow operations
     */
    private String processExtractedFileFast(Path file, String originalName) throws IOException {
        String lowerName = originalName.toLowerCase();

        // IFC - fast, use basic parser
        if (lowerName.endsWith(".ifc")) {
            try (InputStream is = Files.newInputStream(file)) {
                IfcParserService.IfcParseResult ifcResult = ifcParserService.parseIfc(is);
                return ifcResult.getDescription();
            }
        }

        // DXF - fast
        if (lowerName.endsWith(".dxf")) {
            try (InputStream is = Files.newInputStream(file)) {
                DxfParserService.DxfParseResult dxfResult = dxfParserService.parseDxf(is);
                return dxfResult.getDescription();
            }
        }

        // DWG - skip (too slow, suggest DXF export)
        if (lowerName.endsWith(".dwg")) {
            long sizeKb = Files.size(file) / 1024;
            return String.format("DWG fail (%d KB) - ekspordi DXF formaati kiiremaks analüüsiks.", sizeKb);
        }

        // RVT - skip
        if (lowerName.endsWith(".rvt")) {
            long sizeKb = Files.size(file) / 1024;
            return String.format("Revit fail (%d KB) - ekspordi IFC formaati.", sizeKb);
        }

        // PDF - extract text only (skip Vision API for speed)
        if (lowerName.endsWith(".pdf")) {
            return processExtractedPdfFast(file);
        }

        // Images - skip in ZIP (too slow with Vision API)
        if (isImageFile(lowerName)) {
            return "Pildifail - lae üles eraldi detailsemaks analüüsiks.";
        }

        return null;
    }

    /**
     * Fast PDF processing - text only, no Vision API
     */
    private String processExtractedPdfFast(Path pdfFile) throws IOException {
        byte[] pdfBytes = Files.readAllBytes(pdfFile);
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text != null && text.trim().length() > 50) {
                // Limit text length for speed
                String trimmed = text.length() > 3000 ? text.substring(0, 3000) + "..." : text;
                return trimmed;
            }
            return "PDF sisaldab peamiselt pilte - lae üles eraldi Vision AI analüüsiks.";
        }
    }

    /**
     * Check if a file in ZIP is supported
     */
    private boolean isSupportedZipEntry(String lowerFilename) {
        return lowerFilename.endsWith(".ifc") ||
               lowerFilename.endsWith(".dwg") ||
               lowerFilename.endsWith(".dxf") ||
               lowerFilename.endsWith(".rvt") ||
               lowerFilename.endsWith(".pdf") ||
               lowerFilename.endsWith(".jpg") ||
               lowerFilename.endsWith(".jpeg") ||
               lowerFilename.endsWith(".png") ||
               lowerFilename.endsWith(".gif") ||
               lowerFilename.endsWith(".bmp") ||
               lowerFilename.endsWith(".webp");
    }

    /**
     * Process an extracted file based on its type
     */
    private String processExtractedFile(Path file, String originalName) throws IOException {
        String lowerName = originalName.toLowerCase();

        if (lowerName.endsWith(".ifc")) {
            // Parse IFC file using IfcOpenShell
            try {
                var ifcData = ifcOpenShellParserService.parseIfcFile(file);
                return buildIfcDescription(ifcData, originalName);
            } catch (Exception e) {
                log.warn("IfcOpenShell failed, trying basic parser: {}", e.getMessage());
                try (InputStream is = Files.newInputStream(file)) {
                    IfcParserService.IfcParseResult ifcResult = ifcParserService.parseIfc(is);
                    return ifcResult.getDescription();
                }
            }
        } else if (lowerName.endsWith(".dxf")) {
            // Parse DXF file
            try (InputStream is = Files.newInputStream(file)) {
                DxfParserService.DxfParseResult dxfResult = dxfParserService.parseDxf(is);
                return dxfResult.getDescription();
            }
        } else if (lowerName.endsWith(".dwg")) {
            // Try to convert DWG to DXF
            return processExtractedDwg(file, originalName);
        } else if (lowerName.endsWith(".rvt")) {
            // Revit files cannot be directly parsed
            long sizeKb = Files.size(file) / 1024;
            return String.format("Revit fail (%d KB) - vajab IFC eksporti analüüsiks.", sizeKb);
        } else if (lowerName.endsWith(".pdf")) {
            // Process PDF
            return processExtractedPdf(file);
        } else if (isImageFile(lowerName)) {
            // Process image with Vision API
            return processExtractedImage(file, originalName);
        }

        return null;
    }

    /**
     * Process extracted DWG file
     */
    private String processExtractedDwg(Path dwgFile, String originalName) {
        try {
            Path dxfFile = dwgFile.resolveSibling(
                dwgFile.getFileName().toString().replaceAll("(?i)\\.dwg$", ".dxf")
            );

            ProcessBuilder pb = new ProcessBuilder(
                "dwg2dxf",
                "-o", dxfFile.toAbsolutePath().toString(),
                dwgFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            if (!completed || process.exitValue() != 0 || !Files.exists(dxfFile)) {
                return "DWG fail - automaatne teisendamine ebaõnnestus.";
            }

            try (InputStream is = Files.newInputStream(dxfFile)) {
                DxfParserService.DxfParseResult dxfResult = dxfParserService.parseDxf(is);
                return "DWG teisendatud ja analüüsitud:\n" + dxfResult.getDescription();
            } finally {
                Files.deleteIfExists(dxfFile);
            }
        } catch (Exception e) {
            log.warn("DWG processing failed: {}", e.getMessage());
            return "DWG fail - töötlemine ebaõnnestus.";
        }
    }

    /**
     * Process extracted PDF file
     */
    private String processExtractedPdf(Path pdfFile) throws IOException {
        byte[] pdfBytes = Files.readAllBytes(pdfFile);

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text != null && text.trim().length() > 100) {
                return text;
            }

            // Try Vision API for image-based PDFs
            return extractFromPdfWithVision(document);
        }
    }

    /**
     * Process extracted image file with Vision API
     */
    private String processExtractedImage(Path imageFile, String originalName) throws IOException {
        byte[] imageBytes = Files.readAllBytes(imageFile);
        String mediaType = getMediaType(originalName);

        String visionResponse = anthropicService.callClaudeVision(imageBytes, mediaType, VISION_PROMPT);
        if (visionResponse != null) {
            return "Pilt analüüsitud (Vision AI):\n" + visionResponse;
        }

        return "Pildi analüüs ebaõnnestus.";
    }

    /**
     * Extract from DXF CAD file
     */
    private String extractFromDxf(MultipartFile file) throws IOException {
        log.info("Processing DXF CAD file: {}", file.getOriginalFilename());

        DxfParserService.DxfParseResult dxfResult = dxfParserService.parseDxf(file.getInputStream());
        return dxfResult.getDescription();
    }

    /**
     * Handle binary DWG file - convert to DXF using LibreDWG and parse
     */
    private String handleDwgFile(MultipartFile file) throws IOException {
        log.info("Processing DWG file: {} ({} KB)", file.getOriginalFilename(), file.getSize() / 1024);

        Path tempDir = null;
        try {
            // Create unique temp directory for this conversion
            String uniqueId = java.util.UUID.randomUUID().toString().substring(0, 8);
            tempDir = Files.createTempDirectory("dwg_convert_" + uniqueId);

            // Save uploaded DWG file
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = "drawing.dwg";
            }
            Path dwgFile = tempDir.resolve(filename);
            Path dxfFile = tempDir.resolve(filename.replaceAll("(?i)\\.dwg$", ".dxf"));

            Files.write(dwgFile, file.getBytes());
            log.info("Saved DWG to: {}", dwgFile);

            // Convert DWG to DXF using LibreDWG's dwg2dxf
            ProcessBuilder pb = new ProcessBuilder(
                "dwg2dxf",
                "-o", dxfFile.toAbsolutePath().toString(),
                dwgFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read process output for logging
            String output = new String(process.getInputStream().readAllBytes());
            boolean completed = process.waitFor(120, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("DWG conversion timed out after 120 seconds");
                return getDwgFallbackMessage(file);
            }

            int exitCode = process.exitValue();
            log.info("dwg2dxf exit code: {}, output: {}", exitCode, output.trim());

            // Check if DXF was created
            if (exitCode != 0 || !Files.exists(dxfFile)) {
                log.error("DWG conversion failed - exit code: {}, dxf exists: {}", exitCode, Files.exists(dxfFile));
                return getDwgFallbackMessage(file);
            }

            // Parse the converted DXF file
            log.info("DWG converted successfully, parsing DXF: {}", dxfFile.getFileName());
            try (FileInputStream fis = new FileInputStream(dxfFile.toFile())) {
                DxfParserService.DxfParseResult dxfResult = dxfParserService.parseDxf(fis);
                String description = dxfResult.getDescription();

                return String.format("""
                    DWG fail "%s" edukalt teisendatud ja analüüsitud:

                    %s
                    """, filename, description);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("DWG conversion interrupted", e);
            return getDwgFallbackMessage(file);
        } catch (Exception e) {
            log.error("Error processing DWG file: {}", e.getMessage(), e);
            return getDwgFallbackMessage(file);
        } finally {
            // Cleanup temp directory
            cleanupDirectory(tempDir);
        }
    }

    /**
     * Recursively cleanup a directory
     */
    private void cleanupDirectory(Path dir) {
        if (dir == null) return;
        try {
            Files.walk(dir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }

    /**
     * Fallback message when DWG conversion fails
     */
    private String getDwgFallbackMessage(MultipartFile file) {
        long sizeKb = file.getSize() / 1024;
        return String.format("""
            DWG fail (AutoCAD joonis) - %d KB

            DWG faili automaatne teisendamine ebaõnnestus.

            Palun proovi üht järgnevatest:
            1. Ekspordi fail DXF formaati AutoCAD-is (File -> Export -> DXF)
            2. Ekspordi PDF-ina (Print -> PDF)
            3. Salvesta joonisest pilt (PNG/JPG)

            Fail: %s

            DXF, PDF ja pildifailid analüüsitakse automaatselt.
            """, sizeKb, file.getOriginalFilename());
    }

    /**
     * Handle Revit file - cannot directly parse, provide guidance
     */
    private String handleRevitFile(MultipartFile file) {
        log.warn("Revit file uploaded - proprietary format requires conversion");

        long sizeKb = file.getSize() / 1024;

        return String.format("""
            Revit fail (BIM mudel) - %d KB

            Revit .rvt on Autodesk'i BIM formaat. Faili analüüsimiseks:
            1. Ekspordi fail IFC formaati (File -> Export -> IFC)
            2. Või ekspordi PDF-ina vaadete kaudu
            3. Või ekspordi koguste aruanne (Schedules -> Export)

            Üles laaditi Revit fail nimega: %s

            IFC formaat sisaldab kõiki ehituselemente, koguseid ja materjale,
            mida AI saab automaatselt analüüsida.
            """, sizeKb, file.getOriginalFilename());
    }

    private String extractTextFromPdf(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        }
    }

    // Maximum reasonable per-unit prices by category to prevent unrealistic estimates
    // Units vary: m² for area work, tk for items (windows/doors), jm for linear work
    // Based on Estonian construction market 2024-2025 rates (upper bound with materials)
    private static final Map<String, BigDecimal> MAX_UNIT_PRICE = Map.ofEntries(
        Map.entry("TILING", new BigDecimal("120")),          // €/m² (premium tiles + labor)
        Map.entry("ELECTRICAL", new BigDecimal("80")),        // €/m² (full rewiring)
        Map.entry("PLUMBING", new BigDecimal("150")),         // €/m² or €/tk (full bathroom plumbing)
        Map.entry("FINISHING", new BigDecimal("60")),          // €/m² (premium paint + prep)
        Map.entry("FLOORING", new BigDecimal("100")),          // €/m² (hardwood parquet + install)
        Map.entry("DEMOLITION", new BigDecimal("50")),         // €/m²
        Map.entry("ROOFING", new BigDecimal("150")),           // €/m² (metal roof + insulation)
        Map.entry("HVAC", new BigDecimal("120")),              // €/m² (full system install)
        Map.entry("WINDOWS_DOORS", new BigDecimal("1500")),    // €/tk (triple-glazed window installed)
        Map.entry("FACADE", new BigDecimal("200")),            // €/m² (insulation + cladding)
        Map.entry("GENERAL_CONSTRUCTION", new BigDecimal("150")), // €/m²
        Map.entry("LANDSCAPING", new BigDecimal("80"))         // €/m²
    );

    private void enrichWithMarketPrices(ProjectStageDto stage, String location) {
        Optional<MarketPrice> priceOpt = marketPriceRepository.findByCategoryAndRegion(
            stage.getCategory(), location
        );

        if (priceOpt.isEmpty()) {
            priceOpt = marketPriceRepository.findByCategory(stage.getCategory());
        }

        if (priceOpt.isPresent()) {
            MarketPrice mp = priceOpt.get();
            BigDecimal qty = stage.getQuantity();
            BigDecimal multiplier = mp.getRegionMultiplier() != null ? mp.getRegionMultiplier() : BigDecimal.ONE;

            if (mp.getMinPrice() != null) {
                stage.setPriceEstimateMin(mp.getMinPrice().multiply(qty).multiply(multiplier));
            }
            if (mp.getMaxPrice() != null) {
                stage.setPriceEstimateMax(mp.getMaxPrice().multiply(qty).multiply(multiplier));
            }
            if (mp.getMedianPrice() != null) {
                stage.setPriceEstimateMedian(mp.getMedianPrice().multiply(qty).multiply(multiplier));
            }
        } else {
            // Default fallback prices per category
            setDefaultPrices(stage);
        }

        // Validate and clamp prices to reasonable per-unit limits
        validateAndClampPrices(stage);
    }

    private void validateAndClampPrices(ProjectStageDto stage) {
        BigDecimal qty = stage.getQuantity();
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal maxPerUnit = MAX_UNIT_PRICE.getOrDefault(stage.getCategory(), new BigDecimal("50"));
        BigDecimal maxTotal = maxPerUnit.multiply(qty);

        if (stage.getPriceEstimateMax() != null && stage.getPriceEstimateMax().compareTo(maxTotal) > 0) {
            BigDecimal perUnit = stage.getPriceEstimateMax().divide(qty, 2, java.math.RoundingMode.HALF_UP);
            log.warn("Price estimate too high for category={}, qty={}: €{}/unit (max €{}/unit). Clamping.",
                stage.getCategory(), qty, perUnit, maxPerUnit);
            stage.setPriceEstimateMax(maxTotal);
        }

        if (stage.getPriceEstimateMin() != null && stage.getPriceEstimateMin().compareTo(maxTotal) > 0) {
            stage.setPriceEstimateMin(maxTotal);
        }

        if (stage.getPriceEstimateMedian() != null && stage.getPriceEstimateMedian().compareTo(maxTotal) > 0) {
            stage.setPriceEstimateMedian(maxTotal);
        }

        // Ensure min <= median <= max
        if (stage.getPriceEstimateMin() != null && stage.getPriceEstimateMax() != null
                && stage.getPriceEstimateMin().compareTo(stage.getPriceEstimateMax()) > 0) {
            stage.setPriceEstimateMin(stage.getPriceEstimateMax());
        }
        if (stage.getPriceEstimateMedian() != null && stage.getPriceEstimateMax() != null
                && stage.getPriceEstimateMedian().compareTo(stage.getPriceEstimateMax()) > 0) {
            stage.setPriceEstimateMedian(stage.getPriceEstimateMax());
        }
    }

    private void setDefaultPrices(ProjectStageDto stage) {
        BigDecimal qty = stage.getQuantity();
        BigDecimal minRate, maxRate;

        switch (stage.getCategory()) {
            case "TILING" -> { minRate = new BigDecimal("25"); maxRate = new BigDecimal("45"); }
            case "ELECTRICAL" -> { minRate = new BigDecimal("15"); maxRate = new BigDecimal("30"); }
            case "PLUMBING" -> { minRate = new BigDecimal("20"); maxRate = new BigDecimal("40"); }
            case "FINISHING" -> { minRate = new BigDecimal("8"); maxRate = new BigDecimal("15"); }
            case "FLOORING" -> { minRate = new BigDecimal("12"); maxRate = new BigDecimal("25"); }
            case "DEMOLITION" -> { minRate = new BigDecimal("10"); maxRate = new BigDecimal("20"); }
            case "ROOFING" -> { minRate = new BigDecimal("30"); maxRate = new BigDecimal("60"); }
            case "HVAC" -> { minRate = new BigDecimal("25"); maxRate = new BigDecimal("50"); }
            case "WINDOWS_DOORS" -> { minRate = new BigDecimal("50"); maxRate = new BigDecimal("150"); }
            default -> { minRate = new BigDecimal("15"); maxRate = new BigDecimal("35"); }
        }

        stage.setPriceEstimateMin(minRate.multiply(qty));
        stage.setPriceEstimateMax(maxRate.multiply(qty));
        stage.setPriceEstimateMedian(minRate.add(maxRate).divide(new BigDecimal("2")).multiply(qty));
    }

    private void enrichWithSupplierCount(ProjectStageDto stage, String location) {
        try {
            // Use cached supplier search service for faster lookups
            int count = supplierSearchService.getSupplierCount(stage.getCategory(), location);
            if (count == 0) {
                // Fallback to database count
                count = supplierRepository.countByCategoryAndCity(stage.getCategory(), location);
            }
            if (count == 0) {
                count = supplierRepository.countByCategory(stage.getCategory());
            }
            stage.setSupplierCount(count > 0 ? count : 15); // Default minimum
        } catch (Exception e) {
            log.warn("Error counting suppliers for category {}: {}", stage.getCategory(), e.getMessage());
            // Fallback: realistic count
            stage.setSupplierCount(15);
        }
    }

    private void calculateTotals(ProjectParseResult result) {
        BigDecimal totalMin = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        int totalSuppliers = 0;

        for (ProjectStageDto stage : result.getStages()) {
            if (stage.getPriceEstimateMin() != null) {
                totalMin = totalMin.add(stage.getPriceEstimateMin());
            }
            if (stage.getPriceEstimateMax() != null) {
                totalMax = totalMax.add(stage.getPriceEstimateMax());
            }
            totalSuppliers += stage.getSupplierCount();
        }

        result.setTotalEstimateMin(totalMin);
        result.setTotalEstimateMax(totalMax);
        result.setTotalSupplierCount(totalSuppliers);

        // Calculate materials totals
        BigDecimal materialsTotalMin = BigDecimal.ZERO;
        BigDecimal materialsTotalMax = BigDecimal.ZERO;

        if (result.getDependentMaterials() != null) {
            for (DependentMaterialDto mat : result.getDependentMaterials()) {
                if (mat.getTotalPriceMin() != null) {
                    materialsTotalMin = materialsTotalMin.add(mat.getTotalPriceMin());
                }
                if (mat.getTotalPriceMax() != null) {
                    materialsTotalMax = materialsTotalMax.add(mat.getTotalPriceMax());
                }
            }
        }

        result.setMaterialsTotalMin(materialsTotalMin);
        result.setMaterialsTotalMax(materialsTotalMax);
        result.setGrandTotalMin(totalMin.add(materialsTotalMin));
        result.setGrandTotalMax(totalMax.add(materialsTotalMax));
    }

    private void buildSummary(ProjectParseResult result) {
        List<MaterialSummaryDto> summary = new ArrayList<>();

        // Add work items
        for (ProjectStageDto stage : result.getStages()) {
            MaterialSummaryDto row = new MaterialSummaryDto();
            row.setName(stage.getName());
            row.setType("WORK");
            row.setQuantity(stage.getQuantity());
            row.setUnit(stage.getUnit());
            row.setPriceMin(stage.getPriceEstimateMin());
            row.setPriceMax(stage.getPriceEstimateMax());
            row.setCategory(stage.getCategory());
            summary.add(row);
        }

        // Add material items
        if (result.getDependentMaterials() != null) {
            for (DependentMaterialDto mat : result.getDependentMaterials()) {
                MaterialSummaryDto row = new MaterialSummaryDto();
                row.setName(mat.getMaterialName());
                row.setType("MATERIAL");
                row.setQuantity(mat.getTotalQuantity());
                row.setUnit(mat.getUnit());
                row.setPriceMin(mat.getTotalPriceMin());
                row.setPriceMax(mat.getTotalPriceMax());
                row.setCategory("MATERIAL");
                summary.add(row);
            }
        }

        result.setSummary(summary);
    }

    /**
     * Check if any stage in the result has a PLUMBING or HVAC category.
     */
    private boolean hasMepStages(ProjectParseResult result) {
        if (result == null || result.getStages() == null) return false;
        return result.getStages().stream().anyMatch(s -> {
            String cat = s.getCategory();
            return "PLUMBING".equals(cat) || "HVAC".equals(cat);
        });
    }

    /**
     * Detect pipe systems from page images using a second Vision API call.
     */
    private List<PipeSystemDto> detectPipeSystems(List<byte[]> pageImages, List<String> mediaTypes) {
        try {
            String visionResponse;
            if (pageImages.size() == 1) {
                visionResponse = anthropicService.callClaudeVision(
                    pageImages.get(0), mediaTypes.get(0), PIPE_SYSTEM_PROMPT
                );
            } else {
                visionResponse = anthropicService.callClaudeVisionMultiple(
                    pageImages, mediaTypes,
                    PIPE_SYSTEM_PROMPT + "\n\nThis document has " + pageImages.size() + " pages. Analyze all pages together."
                );
            }

            if (visionResponse == null) {
                log.warn("Pipe system detection returned null response");
                return null;
            }

            String jsonStr = extractJson(visionResponse);
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode systemsNode = root.path("pipeSystems");

            if (!systemsNode.isArray()) {
                log.warn("Pipe system response missing pipeSystems array");
                return null;
            }

            List<PipeSystemDto> systems = new ArrayList<>();
            for (JsonNode sysNode : systemsNode) {
                PipeSystemDto system = new PipeSystemDto();
                system.setSystemCode(sysNode.path("systemCode").asText(""));
                system.setSystemName(sysNode.path("systemName").asText(""));
                system.setSystemType(sysNode.path("systemType").asText(""));
                system.setLengthMeters(new BigDecimal(sysNode.path("lengthMeters").asText("0")));
                system.setPipeSpecs(sysNode.path("pipeSpecs").asText(""));
                system.setConfidence(sysNode.path("confidence").asDouble(0.5));

                List<PipeComponentDto> components = new ArrayList<>();
                JsonNode compsNode = sysNode.path("components");
                if (compsNode.isArray()) {
                    for (JsonNode compNode : compsNode) {
                        PipeComponentDto comp = new PipeComponentDto();
                        comp.setType(compNode.path("type").asText(""));
                        comp.setTypeLabel(compNode.path("typeLabel").asText(""));
                        comp.setDiameterMm(compNode.path("diameterMm").asInt(0));
                        comp.setCount(compNode.path("count").asInt(0));
                        if (!compNode.path("lengthM").isNull() && compNode.path("lengthM").isNumber()) {
                            comp.setLengthM(new BigDecimal(compNode.path("lengthM").asText("0")));
                        }
                        comp.setMaterial(compNode.path("material").asText(""));
                        comp.setConfidence(compNode.path("confidence").asDouble(0.5));
                        components.add(comp);
                    }
                }
                system.setComponents(components);
                systems.add(system);
            }

            log.info("Detected {} pipe systems", systems.size());
            return systems.isEmpty() ? null : systems;

        } catch (Exception e) {
            log.warn("Pipe system detection failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Apply default unit prices to pipe system components based on type and diameter.
     */
    private void enrichPipeSystemPrices(List<PipeSystemDto> systems) {
        for (PipeSystemDto system : systems) {
            BigDecimal systemTotalMin = BigDecimal.ZERO;
            BigDecimal systemTotalMax = BigDecimal.ZERO;

            for (PipeComponentDto comp : system.getComponents()) {
                BigDecimal unitMin;
                BigDecimal unitMax;
                String priceUnit;

                switch (comp.getType()) {
                    case "STRAIGHT" -> {
                        priceUnit = "jm";
                        if (comp.getDiameterMm() <= 50) {
                            unitMin = new BigDecimal("3"); unitMax = new BigDecimal("6");
                        } else if (comp.getDiameterMm() <= 110) {
                            unitMin = new BigDecimal("5"); unitMax = new BigDecimal("12");
                        } else {
                            unitMin = new BigDecimal("8"); unitMax = new BigDecimal("18");
                        }
                    }
                    case "ELBOW_45", "ELBOW_90" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("2"); unitMax = new BigDecimal("10");
                    }
                    case "COUPLING" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("1.50"); unitMax = new BigDecimal("5");
                    }
                    case "TEE" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("3"); unitMax = new BigDecimal("12");
                    }
                    case "REDUCER" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("2"); unitMax = new BigDecimal("8");
                    }
                    case "VALVE" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("15"); unitMax = new BigDecimal("80");
                    }
                    case "MANHOLE" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("150"); unitMax = new BigDecimal("500");
                    }
                    case "DRAIN" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("20"); unitMax = new BigDecimal("80");
                    }
                    case "CLEANOUT" -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("10"); unitMax = new BigDecimal("40");
                    }
                    default -> {
                        priceUnit = "tk"; unitMin = new BigDecimal("5"); unitMax = new BigDecimal("20");
                    }
                }

                comp.setUnitPriceMin(unitMin);
                comp.setUnitPriceMax(unitMax);
                comp.setPriceUnit(priceUnit);

                // Calculate total: for STRAIGHT pipes use lengthM, for fittings use count
                BigDecimal quantity;
                if ("STRAIGHT".equals(comp.getType()) && comp.getLengthM() != null) {
                    quantity = comp.getLengthM();
                } else {
                    quantity = new BigDecimal(comp.getCount());
                }

                comp.setTotalPriceMin(unitMin.multiply(quantity));
                comp.setTotalPriceMax(unitMax.multiply(quantity));

                systemTotalMin = systemTotalMin.add(comp.getTotalPriceMin());
                systemTotalMax = systemTotalMax.add(comp.getTotalPriceMax());
            }

            system.setTotalPriceMin(systemTotalMin);
            system.setTotalPriceMax(systemTotalMax);
        }
    }

    private String extractJson(String response) {
        // Try to find JSON object in the response
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private ProjectParseResult createFallbackResult(String description) {
        log.info("Using fallback parser for description");
        ProjectParseResult result = new ProjectParseResult();
        result.setProjectTitle("Korteri remont");
        result.setLocation(extractLocation(description));

        List<ProjectStageDto> stages = parseDescriptionManually(description, result.getLocation());
        result.setStages(stages);

        return result;
    }

    private String extractLocation(String description) {
        String lower = description.toLowerCase();
        if (lower.contains("tallinn")) return "Tallinn";
        if (lower.contains("tartu")) return "Tartu";
        if (lower.contains("pärnu")) return "Pärnu";
        if (lower.contains("narva")) return "Narva";
        return "Tallinn";
    }

    private List<ProjectStageDto> parseDescriptionManually(String description, String location) {
        List<ProjectStageDto> stages = new ArrayList<>();
        String lower = description.toLowerCase();

        if (lower.contains("plaati") || lower.contains("tiling") || lower.contains("keraami")) {
            BigDecimal qty = extractQuantity(description, "plaati", 20);
            stages.add(createStage("Plaatimistööd", "TILING", qty, "m2",
                "Vannitoa ja/või köögi plaatimine"));
        }

        if (lower.contains("elektr") || lower.contains("electrical") || lower.contains("juhtme")) {
            BigDecimal qty = extractQuantity(description, "elektr", 65);
            stages.add(createStage("Elektritööd", "ELECTRICAL", qty, "m2",
                "Elektrisüsteemi uuendamine või paigaldamine"));
        }

        if (lower.contains("viimistl") || lower.contains("värvi") || lower.contains("sein") || lower.contains("paint")) {
            BigDecimal qty = extractQuantity(description, "viimistl", 180);
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                qty = extractQuantity(description, "sein", 180);
            }
            stages.add(createStage("Viimistlustööd", "FINISHING", qty, "m2",
                "Seinte ja lagede viimistlemine, värvimine"));
        }

        if (lower.contains("põrand") || lower.contains("laminaat") || lower.contains("parkett") || lower.contains("floor")) {
            BigDecimal qty = extractQuantity(description, "põrand", 45);
            if (qty.compareTo(BigDecimal.ZERO) == 0) {
                qty = extractQuantity(description, "laminaat", 45);
            }
            stages.add(createStage("Põrandatööd", "FLOORING", qty, "m2",
                "Põrandakatte paigaldamine"));
        }

        if (lower.contains("sanit") || lower.contains("toru") || lower.contains("plumb") || lower.contains("vesi")) {
            BigDecimal qty = extractQuantity(description, "sanit", 15);
            stages.add(createStage("Sanitaartehnilised tööd", "PLUMBING", qty, "m2",
                "Torustiku ja sanitaartehnika paigaldamine"));
        }

        if (lower.contains("lammut") || lower.contains("demol") || lower.contains("lõhku")) {
            BigDecimal qty = extractQuantity(description, "lammut", 30);
            stages.add(createStage("Lammutustööd", "DEMOLITION", qty, "m2",
                "Vanade konstruktsioonide ja viimistluse eemaldamine"));
        }

        if (stages.isEmpty()) {
            stages.add(createStage("Üldehitustööd", "GENERAL_CONSTRUCTION",
                new BigDecimal("50"), "m2", "Üldised ehitustööd"));
        }

        return stages;
    }

    private BigDecimal extractQuantity(String description, String keyword, int defaultValue) {
        String lower = description.toLowerCase();
        int idx = lower.indexOf(keyword);
        if (idx == -1) return new BigDecimal(defaultValue);

        // Look for number pattern near the keyword (within 30 chars before/after)
        int start = Math.max(0, idx - 30);
        int end = Math.min(description.length(), idx + keyword.length() + 30);
        String context = description.substring(start, end);

        // Match patterns like "20m2", "20 m2", "20m²"
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\\s*m[2²]?");
        java.util.regex.Matcher matcher = pattern.matcher(context);
        if (matcher.find()) {
            return new BigDecimal(matcher.group(1));
        }

        return new BigDecimal(defaultValue);
    }

    private ProjectStageDto createStage(String name, String category, BigDecimal quantity,
                                        String unit, String description) {
        ProjectStageDto stage = new ProjectStageDto();
        stage.setName(name);
        stage.setCategory(category);
        stage.setQuantity(quantity);
        stage.setUnit(unit);
        stage.setDescription(description);
        stage.setDependencies(new ArrayList<>());
        return stage;
    }
}
