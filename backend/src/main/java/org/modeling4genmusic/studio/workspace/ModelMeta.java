package org.modeling4genmusic.studio.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bookkeeping data of a model, kept next to the XMI file so that the semantic model stays clean.
 * {@code role} is an optional free label that tells apart models of the same metamodel.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelMeta(String name, String metamodel, int version, String role) {

    public ModelMeta nextVersion(String newName, String newRole) {
        return new ModelMeta(newName, metamodel, version + 1, newRole);
    }
}
