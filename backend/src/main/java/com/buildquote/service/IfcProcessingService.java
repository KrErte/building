package com.buildquote.service;

import com.buildquote.config.IfcParserProperties;
import com.buildquote.dto.ifc.IfcBuildingData;
import com.buildquote.exception.IfcFileTooLargeException;
import com.buildquote.exception.IfcParseException;
import com.buildquote.exception.InvalidIfcFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Orchestrator service for IFC file processing.
 * Handles validation, async processing, and job tracking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IfcProcessingService {

    private final IfcOpenShellParserService ifcParserService;
    private final IfcParserProperties properties;

    // Job tracking for async processing
    private final Map<String, IfcProcessingJob> jobs = new ConcurrentHashMap<>();

    /**
     * Process an IFC file synchronously.
     * Suitable for smaller files (< 10MB).
     *
     * @param file The uploaded IFC file
     * @return Parsed building data
     */
    public IfcBuildingData processIfcFileSync(MultipartFile file) {
        validateFile(file);
        Path tempFile = saveToTempFile(file);
        try {
            return ifcParserService.parseIfcFile(tempFile);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    /**
     * Process an IFC file asynchronously.
     * Suitable for larger files.
     *
     * @param file The uploaded IFC file
     * @return CompletableFuture with the parsed building data
     */
    @Async
    public CompletableFuture<IfcBuildingData> processIfcFile(MultipartFile file) {
        validateFile(file);
        Path tempFile = saveToTempFile(file);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return ifcParserService.parseIfcFile(tempFile);
            } finally {
                deleteTempFile(tempFile);
            }
        });
    }

    /**
     * Start async processing and return a job ID for tracking.
     *
     * @param file The uploaded IFC file
     * @return Job ID
     */
    public String startProcessingJob(MultipartFile file) {
        validateFile(file);

        String jobId = UUID.randomUUID().toString();
        Path tempFile = saveToTempFile(file);

        IfcProcessingJob job = new IfcProcessingJob(jobId, file.getOriginalFilename());
        jobs.put(jobId, job);

        // Start async processing
        CompletableFuture.runAsync(() -> {
            try {
                job.setStatus(IfcProcessingStatus.PROCESSING);
                IfcBuildingData result = ifcParserService.parseIfcFile(tempFile);
                job.setResult(result);
                job.setStatus(IfcProcessingStatus.COMPLETED);
                log.info("IFC job {} completed successfully", jobId);
            } catch (Exception e) {
                job.setError(e.getMessage());
                job.setStatus(IfcProcessingStatus.FAILED);
                log.error("IFC job {} failed: {}", jobId, e.getMessage());
            } finally {
                deleteTempFile(tempFile);
            }
        });

        return jobId;
    }

    /**
     * Get the status of a processing job.
     *
     * @param jobId Job ID
     * @return Job status or null if not found
     */
    public IfcProcessingJob getJob(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Remove a completed job from tracking.
     *
     * @param jobId Job ID
     */
    public void removeJob(String jobId) {
        jobs.remove(jobId);
    }

    private void validateFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new InvalidIfcFileException("Failinimi puudub");
        }

        String lowerFilename = filename.toLowerCase();

        // Check extension - accept both .ifc and .zip
        if (!lowerFilename.endsWith(".ifc") && !lowerFilename.endsWith(".zip")) {
            throw new InvalidIfcFileException("Fail peab olema .ifc või .zip formaadis. Saadi: " + filename);
        }

        // Check file size
        long fileSizeMb = file.getSize() / (1024 * 1024);
        if (fileSizeMb > properties.maxFileSizeMb()) {
            throw new IfcFileTooLargeException(fileSizeMb, properties.maxFileSizeMb());
        }

        // For IFC files, check magic string
        if (lowerFilename.endsWith(".ifc")) {
            validateIfcContent(file);
        }
        // For ZIP files, we'll validate after extraction
    }

    private void validateIfcContent(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.contains("ISO-10303-21")) {
                throw new InvalidIfcFileException("Vigane IFC fail. Fail ei sisalda IFC päist (ISO-10303-21).");
            }
        } catch (IOException e) {
            throw new InvalidIfcFileException("Faili lugemine ebaõnnestus: " + e.getMessage(), e);
        }
    }

    private void validateIfcFile(Path ifcFile) {
        try (BufferedReader reader = Files.newBufferedReader(ifcFile)) {
            String firstLine = reader.readLine();
            if (firstLine == null || !firstLine.contains("ISO-10303-21")) {
                throw new InvalidIfcFileException("ZIP-ist leitud fail ei ole kehtiv IFC fail (puudub ISO-10303-21 päis).");
            }
        } catch (IOException e) {
            throw new InvalidIfcFileException("IFC faili valideerimine ebaõnnestus: " + e.getMessage(), e);
        }
    }

    private Path saveToTempFile(MultipartFile file) {
        try {
            Path tempDir = Path.of(properties.tempDir());
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
            }

            String filename = file.getOriginalFilename();
            if (filename == null) {
                filename = "upload.ifc";
            }

            String lowerFilename = filename.toLowerCase();

            // Handle ZIP files - extract IFC from inside
            if (lowerFilename.endsWith(".zip")) {
                return extractIfcFromZip(file, tempDir);
            }

            // Regular IFC file
            String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + filename;
            Path tempFile = tempDir.resolve(uniqueName);
            Files.write(tempFile, file.getBytes());

            log.debug("Saved uploaded IFC to: {}", tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new IfcParseException("Failed to save uploaded file: " + e.getMessage(), e);
        }
    }

    /**
     * Extract IFC file from a ZIP archive.
     * Finds the first .ifc file in the ZIP and extracts it.
     *
     * @param zipFile The uploaded ZIP file
     * @param tempDir Directory to extract to
     * @return Path to the extracted IFC file
     */
    private Path extractIfcFromZip(MultipartFile zipFile, Path tempDir) {
        String zipFilename = zipFile.getOriginalFilename();
        log.info("Extracting IFC from ZIP: {}", zipFilename);

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            Path extractedIfcFile = null;
            long totalExtractedSize = 0;
            long maxExtractedSize = (long) properties.maxFileSizeMb() * 1024 * 1024 * 2; // Allow 2x for extraction

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();

                // Skip directories and non-IFC files
                if (entry.isDirectory()) {
                    continue;
                }

                // Security: prevent zip slip attack
                String normalizedName = Path.of(entryName).getFileName().toString();

                // Skip hidden files and macOS metadata
                if (normalizedName.startsWith(".") || normalizedName.startsWith("__MACOSX")) {
                    continue;
                }

                // Look for IFC files
                if (normalizedName.toLowerCase().endsWith(".ifc")) {
                    // Check size limit during extraction
                    if (entry.getSize() > 0) {
                        totalExtractedSize += entry.getSize();
                        if (totalExtractedSize > maxExtractedSize) {
                            throw new IfcFileTooLargeException(
                                totalExtractedSize / (1024 * 1024),
                                properties.maxFileSizeMb()
                            );
                        }
                    }

                    // Extract the IFC file
                    String uniqueName = UUID.randomUUID().toString().substring(0, 8) + "_" + normalizedName;
                    extractedIfcFile = tempDir.resolve(uniqueName);

                    // Extract with size limit check
                    try (InputStream entryStream = zis) {
                        Files.copy(zis, extractedIfcFile, StandardCopyOption.REPLACE_EXISTING);
                    }

                    log.info("Extracted IFC from ZIP: {} -> {}", entryName, extractedIfcFile.getFileName());

                    // Validate the extracted IFC file
                    validateIfcFile(extractedIfcFile);

                    // Return the first valid IFC file found
                    return extractedIfcFile;
                }

                zis.closeEntry();
            }

            // No IFC file found in ZIP
            if (extractedIfcFile == null) {
                throw new InvalidIfcFileException("ZIP-arhiivist ei leitud ühtegi .ifc faili. " +
                    "Palun veendu, et ZIP sisaldab IFC faili.");
            }

            return extractedIfcFile;

        } catch (IOException e) {
            throw new IfcParseException("ZIP-faili lahtipakkimine ebaõnnestus: " + e.getMessage(), e);
        }
    }

    private void deleteTempFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {}
    }

    /**
     * IFC processing job status.
     */
    public enum IfcProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    /**
     * IFC processing job tracker.
     */
    public static class IfcProcessingJob {
        private final String jobId;
        private final String fileName;
        private final long startTime;
        private IfcProcessingStatus status;
        private IfcBuildingData result;
        private String error;

        public IfcProcessingJob(String jobId, String fileName) {
            this.jobId = jobId;
            this.fileName = fileName;
            this.startTime = System.currentTimeMillis();
            this.status = IfcProcessingStatus.PENDING;
        }

        // Getters and setters
        public String getJobId() { return jobId; }
        public String getFileName() { return fileName; }
        public long getStartTime() { return startTime; }
        public IfcProcessingStatus getStatus() { return status; }
        public void setStatus(IfcProcessingStatus status) { this.status = status; }
        public IfcBuildingData getResult() { return result; }
        public void setResult(IfcBuildingData result) { this.result = result; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }

        public long getElapsedMs() {
            return System.currentTimeMillis() - startTime;
        }
    }
}
