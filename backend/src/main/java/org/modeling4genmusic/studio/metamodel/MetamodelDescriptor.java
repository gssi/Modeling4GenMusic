package org.modeling4genmusic.studio.metamodel;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON view of a metamodel, used by the frontend to build palette, forms and connection rules.
 * Features are flattened: every class lists its own and its inherited attributes and references.
 */
public record MetamodelDescriptor(String nsURI, String prefix, String name, List<ClassDescriptor> classes,
        List<EnumDescriptor> enums) {

    /** Reference to a classifier, possibly defined in another metamodel. */
    public record TypeRef(String metamodel, String name) {
    }

    public record ClassDescriptor(String name, @JsonProperty("abstract") boolean isAbstract, List<TypeRef> supertypes,
            List<AttributeDescriptor> attributes, List<ReferenceDescriptor> references) {
    }

    /** {@code type} is one of string, int, double, boolean, enum. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttributeDescriptor(String name, String type, TypeRef enumType, List<String> literals, int lower,
            int upper, Object defaultValue) {
    }

    public record ReferenceDescriptor(String name, TypeRef target, boolean containment, int lower, int upper) {
    }

    public record EnumDescriptor(String name, List<String> literals) {
    }

    /** Short entry for the metamodel list. */
    public record Summary(String nsURI, String prefix, String name) {
    }
}
