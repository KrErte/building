package com.buildquote.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for IFC parser using IfcOpenShell Python script.
 */
@ConfigurationProperties(prefix = "buildquote.ifc.parser")
public record IfcParserProperties(
    String scriptPath,
    String pythonBinary,
    String tempDir,
    int timeoutSeconds,
    int maxFileSizeMb,
    int cleanupAfterMinutes
) {
    public IfcParserProperties {
        // Defaults
        if (scriptPath == null || scriptPath.isBlank()) {
            scriptPath = "/opt/buildquote/scripts/extract_ifc.py";
        }
        if (pythonBinary == null || pythonBinary.isBlank()) {
            pythonBinary = "python3";
        }
        if (tempDir == null || tempDir.isBlank()) {
            tempDir = System.getProperty("java.io.tmpdir") + "/buildquote/ifc";
        }
        if (timeoutSeconds <= 0) {
            timeoutSeconds = 300;
        }
        if (maxFileSizeMb <= 0) {
            maxFileSizeMb = 200;
        }
        if (cleanupAfterMinutes <= 0) {
            cleanupAfterMinutes = 30;
        }
    }
}
