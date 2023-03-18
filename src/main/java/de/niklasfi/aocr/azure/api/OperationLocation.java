package de.niklasfi.aocr.azure.api;

import java.util.regex.Pattern;

public record OperationLocation(String operationId) {

    private static final Pattern fullUrlPattern = Pattern.compile("/vision/v3\\.2/read/analyzeResults/([0-9a-z-]+)$");

    public static OperationLocation fromFullUrl(String fullUrl) {
        final var m = fullUrlPattern.matcher(fullUrl);

        if (!m.find()) {
            throw new RuntimeException("url does not contain operationId");
        }
        if (m.groupCount() < 1) {
            throw new RuntimeException("regex match of url did not contain requisite number of groups");
        }
        return new OperationLocation(m.group(1));
    }
}
