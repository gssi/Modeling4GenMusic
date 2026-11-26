package org.modeling4genmusic.studio.model;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Generic JSON form of a model, the contract between backend and frontend.
 * {@code contents} and {@code references} always map a feature name to a list, whatever its upper bound.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelDocument(String id, String name, String metamodel, String role, Integer version,
        List<ObjectNode> roots) {

    public record ObjectNode(String id, String eClass, Map<String, Object> attributes,
            Map<String, List<ObjectNode>> contents, Map<String, List<Ref>> references) {

        public ObjectNode {
            attributes = attributes == null ? Map.of() : attributes;
            contents = contents == null ? Map.of() : contents;
            references = references == null ? Map.of() : references;
        }
    }

    /** {@code model} is null for a target in the same model; {@code unresolved} marks a dangling target. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Ref(String model, String id, Boolean unresolved) {
    }

    public ModelDocument withRoots(List<ObjectNode> newRoots) {
        return new ModelDocument(id, name, metamodel, role, version, newRoots);
    }
}
