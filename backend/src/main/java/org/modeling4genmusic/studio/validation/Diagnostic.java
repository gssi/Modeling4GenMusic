package org.modeling4genmusic.studio.validation;

import java.util.List;

/**
 * A validation finding on one element of a model. The id is deterministic (model, constraint, element)
 * so that a later request can point at the same finding after validation is run again.
 */
public record Diagnostic(String id, String modelId, String elementId, Severity severity, String constraint,
        String message, List<Fix> fixes) {

    public enum Severity {
        ERROR, WARNING, INFO
    }

    public record Fix(String title) {
    }

    public static String idOf(String modelId, String constraint, String elementId) {
        return modelId + "#" + constraint + "@" + (elementId == null ? "" : elementId);
    }
}
