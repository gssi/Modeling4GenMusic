package org.modeling4genmusic.studio.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One saved state of a model, kept in the history of its workspace.
 *
 * @param savedAt      ISO-8601 instant of the save
 * @param origin       what produced the version: {@code CREATED}, {@code IMPORTED}, {@code SAVED},
 *                     {@code FIXED}, {@code DERIVED} or {@code RESTORED}; null when it is not known
 * @param restoredFrom the version whose content was brought back, for {@code RESTORED}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelVersion(int version, String name, String role, String savedAt, String origin,
        Integer restoredFrom) {

    public static final String CREATED = "CREATED";
    public static final String IMPORTED = "IMPORTED";
    public static final String SAVED = "SAVED";
    public static final String FIXED = "FIXED";
    public static final String DERIVED = "DERIVED";
    public static final String RESTORED = "RESTORED";
}
