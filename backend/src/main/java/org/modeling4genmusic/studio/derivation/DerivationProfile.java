package org.modeling4genmusic.studio.derivation;

import java.util.List;
import java.util.Map;

/**
 * Content of {@code epsilon/derivation.json}: the transformation script, the models it reads and writes
 * and the names under which the script sees them.
 *
 * @param script  ETL file, relative to the {@code epsilon/} resource folder
 * @param inputs  models read by the script
 * @param outputs models produced by the script, saved in this order
 * @param tools   helper objects the script can call: variable name to class name (may be null)
 */
public record DerivationProfile(String script, List<Input> inputs, List<Output> outputs,
        Map<String, String> tools) {

    /**
     * @param alias     name of the model inside the script
     * @param metamodel nsURI the model must conform to
     * @param parameter request field that carries the model id
     * @param primary   the input the outputs are named after; its residual diagnostics are returned
     * @param validated whether errors in this model block the derivation
     */
    public record Input(String alias, String metamodel, String parameter, boolean primary, boolean validated) {
    }

    /**
     * @param alias     name of the model inside the script
     * @param metamodel nsURI of the produced model
     * @param idSuffix  appended to the id of the primary input to form the model id
     * @param name      model name, with {@code %s} replaced by the name of the primary input
     * @param role      role of the produced model (may be null)
     * @param result    response field that carries the id of the produced model
     * @param validated whether the diagnostics of the produced model are returned
     */
    public record Output(String alias, String metamodel, String idSuffix, String name, String role, String result,
            boolean validated) {
    }

    public Input primary() {
        return inputs.stream().filter(Input::primary).findFirst()
                .orElseThrow(() -> new IllegalStateException("The derivation descriptor has no primary input"));
    }
}
