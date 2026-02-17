package com.buildquote.service;

import com.buildquote.config.IfcParserProperties;
import com.buildquote.dto.ifc.IfcBuildingData;
import com.buildquote.exception.IfcParseException;
import com.buildquote.exception.IfcProcessingTimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Service for parsing IFC files using IfcOpenShell Python script.
 * Calls the Python extract_ifc.py script via ProcessBuilder and deserializes the JSON result.
 */
@Service
@Slf4j
public class IfcOpenShellParserService {

    private final IfcParserProperties properties;
    private final ObjectMapper objectMapper;

    public IfcOpenShellParserService(IfcParserProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        initializeTempDirectory();
    }

    private void initializeTempDirectory() {
        try {
            Path tempDir = Path.of(properties.tempDir());
            if (!Files.exists(tempDir)) {
                Files.createDirectories(tempDir);
                log.info("Created IFC temp directory: {}", tempDir);
            }
        } catch (IOException e) {
            log.warn("Failed to create IFC temp directory: {}", e.getMessage());
        }
    }

    /**
     * Parse an IFC file using IfcOpenShell Python script.
     *
     * @param ifcFile Path to the IFC file
     * @return Parsed building data
     * @throws IfcParseException if parsing fails
     * @throws IfcProcessingTimeoutException if parsing times out
     */
    public IfcBuildingData parseIfcFile(Path ifcFile) throws IfcParseException, IfcProcessingTimeoutException {
        String filename = ifcFile.getFileName().toString();
        long fileSizeMb = 0;
        try {
            fileSizeMb = Files.size(ifcFile) / (1024 * 1024);
        } catch (IOException ignored) {}

        log.info("Processing IFC: {}, {}MB", filename, fileSizeMb);

        // Create unique temp directory for this job
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        Path jobDir = Path.of(properties.tempDir(), jobId);
        Path outputJson = jobDir.resolve("output.json");

        try {
            Files.createDirectories(jobDir);

            // Build the process command
            ProcessBuilder pb = new ProcessBuilder(
                properties.pythonBinary(),
                properties.scriptPath(),
                ifcFile.toAbsolutePath().toString(),
                outputJson.toAbsolutePath().toString()
            );

            pb.directory(jobDir.toFile());
            pb.redirectErrorStream(false);

            log.debug("Executing: {} {} {} {}",
                properties.pythonBinary(),
                properties.scriptPath(),
                ifcFile.toAbsolutePath(),
                outputJson.toAbsolutePath());

            Process process = pb.start();

            // Read stderr in a separate thread to prevent blocking
            StringBuilder stderrOutput = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderrOutput.append(line).append("\n");
                    }
                } catch (IOException e) {
                    stderrOutput.append("Error reading stderr: ").append(e.getMessage());
                }
            });
            stderrReader.start();

            // Wait for process with timeout
            boolean completed = process.waitFor(properties.timeoutSeconds(), TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                log.error("IFC parsing timed out after {}s for file: {}", properties.timeoutSeconds(), filename);
                throw new IfcProcessingTimeoutException(properties.timeoutSeconds());
            }

            // Wait for stderr reader to finish
            stderrReader.join(1000);

            int exitCode = process.exitValue();
            String stderr = stderrOutput.toString().trim();

            // Log stderr output (contains progress info)
            if (!stderr.isEmpty()) {
                if (exitCode == 0) {
                    log.info("IFC parser output: {}", stderr);
                } else {
                    log.error("IFC parser error: {}", stderr);
                }
            }

            // Check exit code
            if (exitCode != 0) {
                throw new IfcParseException("IFC parsing failed with exit code " + exitCode, stderr);
            }

            // Check if output file exists
            if (!Files.exists(outputJson)) {
                throw new IfcParseException("IFC parser did not produce output file", stderr);
            }

            // Read and deserialize JSON
            byte[] jsonBytes = Files.readAllBytes(outputJson);
            IfcBuildingData result = objectMapper.readValue(jsonBytes, IfcBuildingData.class);

            log.info("IFC parsing complete: {} structural + {} MEP elements in {}ms",
                result.quantitySummary() != null ? result.quantitySummary().totalElements() : 0,
                result.quantitySummary() != null ? result.quantitySummary().totalMepElements() : 0,
                result.parseTimeMs());

            return result;

        } catch (IOException e) {
            log.error("IO error during IFC parsing: {}", e.getMessage(), e);
            throw new IfcParseException("Failed to process IFC file: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("IFC parsing interrupted", e);
            throw new IfcParseException("IFC parsing was interrupted", e);
        } finally {
            // Cleanup job directory
            cleanupDirectory(jobDir);
        }
    }

    /**
     * Cleanup old temp files periodically.
     * Runs every 10 minutes.
     */
    @Scheduled(fixedRate = 600000)
    public void cleanupTempFiles() {
        Path tempDir = Path.of(properties.tempDir());
        if (!Files.exists(tempDir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(properties.cleanupAfterMinutes(), ChronoUnit.MINUTES);
        int cleaned = 0;

        try (Stream<Path> paths = Files.list(tempDir)) {
            for (Path path : paths.toList()) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    if (attrs.creationTime().toInstant().isBefore(cutoff)) {
                        cleanupDirectory(path);
                        cleaned++;
                    }
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            log.warn("Error during temp cleanup: {}", e.getMessage());
        }

        if (cleaned > 0) {
            log.info("Cleaned up {} old IFC temp directories", cleaned);
        }
    }

    private void cleanupDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted((a, b) -> -a.compareTo(b))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
