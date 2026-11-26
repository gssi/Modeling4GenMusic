package org.modeling4genmusic.studio.validation;

import java.util.List;
import java.util.Map;

/**
 * One entry of {@code epsilon/validations.json}: which EVL script validates the models of a metamodel
 * and under which names the script sees the validated model and the models related to it.
 *
 * @param metamodel   nsURI of the models the entry applies to
 * @param exceptRoles model roles the entry does not apply to (may be null)
 * @param script      EVL file, relative to the {@code epsilon/} resource folder
 * @param model       name of the validated model inside the script
 * @param related     other models the script needs, reached through a reference of the root elements
 * @param tools       helper objects the script can call: variable name to class with a public no-argument
 *                    constructor (may be null)
 */
public record ValidationProfile(String metamodel, List<String> exceptRoles, String script, String model,
        List<Related> related, Map<String, String> tools) {

    /**
     * @param alias     name of the related model inside the script
     * @param metamodel nsURI of the related model
     * @param reference feature of the root elements whose target lives in the related model
     */
    public record Related(String alias, String metamodel, String reference) {
    }

    public boolean appliesTo(String modelMetamodel, String role) {
        return metamodel.equals(modelMetamodel) && (exceptRoles == null || role == null || !exceptRoles.contains(role));
    }
}
