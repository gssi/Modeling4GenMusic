package org.modeling4genmusic.studio.workspace;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Bookkeeping data of a model, kept next to the XMI file so that the semantic model stays clean.
 * {@code role} is an optional free label that tells apart models of the same metamodel.
 * {@code folder} is where the model is shown in the workspace, with {@code /} between levels: it is a
 * label, the files of all the models stay side by side, so that moving a model changes no reference.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelMeta(String name, String metamodel, int version, String role, String folder) {

    public ModelMeta nextVersion(String newName, String newRole) {
        return new ModelMeta(newName, metamodel, version + 1, newRole, folder);
    }

    /** The same model elsewhere in the workspace: its content, and so its version, do not change. */
    public ModelMeta inFolder(String newFolder) {
        return new ModelMeta(name, metamodel, version, role, newFolder);
    }
}
