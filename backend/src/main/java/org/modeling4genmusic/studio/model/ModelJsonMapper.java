package org.modeling4genmusic.studio.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;

/**
 * Converts between dynamic EMF objects and the generic JSON form. It only reads the metamodel
 * reflectively: no domain class or feature is named here.
 */
public final class ModelJsonMapper {

    private ModelJsonMapper() {
    }

    // ---------------------------------------------------------------- EMF -> JSON

    public static List<ModelDocument.ObjectNode> toJson(XMLResource resource) {
        List<ModelDocument.ObjectNode> roots = new ArrayList<>();
        for (EObject root : resource.getContents()) {
            roots.add(toJson(root, resource));
        }
        return roots;
    }

    private static ModelDocument.ObjectNode toJson(EObject object, XMLResource resource) {
        EClass eClass = object.eClass();
        Map<String, Object> attributes = new LinkedHashMap<>();
        for (EAttribute attribute : eClass.getEAllAttributes()) {
            if (!MetamodelRegistry.isEditable(attribute)) {
                continue;
            }
            Object value = object.eGet(attribute);
            if (attribute.isMany()) {
                List<Object> values = new ArrayList<>();
                for (Object item : (List<?>) value) {
                    values.add(attributeToJson(attribute.getEAttributeType(), item));
                }
                attributes.put(attribute.getName(), values);
            } else if (value != null) {
                attributes.put(attribute.getName(), attributeToJson(attribute.getEAttributeType(), value));
            }
        }
        Map<String, List<ModelDocument.ObjectNode>> contents = new LinkedHashMap<>();
        Map<String, List<ModelDocument.Ref>> references = new LinkedHashMap<>();
        for (EReference reference : eClass.getEAllReferences()) {
            if (!MetamodelRegistry.isEditable(reference)) {
                continue;
            }
            List<EObject> targets = targets(object, reference);
            if (targets.isEmpty()) {
                continue;
            }
            if (reference.isContainment()) {
                contents.put(reference.getName(), targets.stream().map(t -> toJson(t, resource)).toList());
            } else {
                references.put(reference.getName(), targets.stream().map(t -> toRef(t, resource)).toList());
            }
        }
        return new ModelDocument.ObjectNode(resource.getID(object), eClass.getName(), attributes, contents,
                references);
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> targets(EObject object, EReference reference) {
        Object value = object.eGet(reference);
        if (reference.isMany()) {
            return new ArrayList<>((EList<EObject>) value);
        }
        return value == null ? List.of() : List.of((EObject) value);
    }

    private static ModelDocument.Ref toRef(EObject target, XMLResource resource) {
        if (target.eIsProxy()) {
            URI uri = EcoreUtil.getURI(target);
            return new ModelDocument.Ref(modelIdOf(uri), uri.fragment(), true);
        }
        Resource targetResource = target.eResource();
        if (targetResource == resource) {
            return new ModelDocument.Ref(null, resource.getID(target), null);
        }
        if (targetResource == null) {
            return new ModelDocument.Ref(null, null, true);
        }
        return new ModelDocument.Ref(modelIdOf(targetResource.getURI()), targetResource.getURIFragment(target), null);
    }

    /** Model id of a model file URI: the file name without its {@code .xmi} extension. */
    public static String modelIdOf(URI uri) {
        String segment = URI.decode(uri.trimFragment().lastSegment());
        return segment.endsWith(".xmi") ? segment.substring(0, segment.length() - 4) : segment;
    }

    private static Object attributeToJson(EDataType dataType, Object value) {
        if (value instanceof EEnumLiteral literal) {
            return literal.getName();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return EcoreUtil.convertToString(dataType, value);
    }

    // ---------------------------------------------------------------- JSON -> EMF

    /**
     * Fills an empty resource from the JSON roots.
     *
     * @param externalResolver resolves {@code (modelId, elementId)} to an object of another model, or null
     */
    public static void fromJson(List<ModelDocument.ObjectNode> roots, XMLResource resource, EPackage ePackage,
            BiFunction<String, String, EObject> externalResolver) {
        Map<String, EObject> byId = new LinkedHashMap<>();
        List<Runnable> referenceSetters = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        for (ModelDocument.ObjectNode root : roots == null ? List.<ModelDocument.ObjectNode>of() : roots) {
            EObject object = build(root, null, resource, ePackage, byId, seenIds, referenceSetters,
                    externalResolver);
            resource.getContents().add(object);
        }
        byId.forEach((id, object) -> resource.setID(object, id));
        referenceSetters.forEach(Runnable::run);
    }

    private static EObject build(ModelDocument.ObjectNode node, EReference containing, XMLResource resource,
            EPackage ePackage, Map<String, EObject> byId, Set<String> seenIds, List<Runnable> referenceSetters,
            BiFunction<String, String, EObject> externalResolver) {
        EClass eClass = resolveClass(node.eClass(), containing, ePackage);
        if (eClass.isAbstract() || eClass.isInterface()) {
            throw ApiException.badRequest("Class " + eClass.getName() + " is abstract and cannot be instantiated");
        }
        EObject object = EcoreUtil.create(eClass);
        String id = node.id() == null || node.id().isBlank() ? EcoreUtil.generateUUID() : node.id();
        if (!seenIds.add(id)) {
            throw ApiException.badRequest("Duplicate element id: " + id);
        }
        byId.put(id, object);

        node.attributes().forEach((name, value) -> {
            EStructuralFeature feature = eClass.getEStructuralFeature(name);
            if (!(feature instanceof EAttribute attribute) || !MetamodelRegistry.isEditable(feature)) {
                throw ApiException.badRequest("Class " + eClass.getName() + " has no attribute '" + name + "'");
            }
            setAttribute(object, attribute, value, id);
        });

        node.contents().forEach((name, children) -> {
            EReference reference = reference(eClass, name, true);
            checkUpper(reference, children.size(), id);
            for (ModelDocument.ObjectNode child : children) {
                EObject childObject = build(child, reference, resource, ePackage, byId, seenIds, referenceSetters,
                        externalResolver);
                checkType(reference, childObject, id);
                add(object, reference, childObject);
            }
        });

        node.references().forEach((name, refs) -> {
            EReference reference = reference(eClass, name, false);
            checkUpper(reference, refs.size(), id);
            referenceSetters.add(() -> {
                for (ModelDocument.Ref ref : refs) {
                    EObject target = resolve(ref, byId, externalResolver, id, name);
                    checkType(reference, target, id);
                    add(object, reference, target);
                }
            });
        });
        return object;
    }

    private static EObject resolve(ModelDocument.Ref ref, Map<String, EObject> byId,
            BiFunction<String, String, EObject> externalResolver, String ownerId, String feature) {
        EObject target = ref.model() == null ? byId.get(ref.id())
                : externalResolver == null ? null : externalResolver.apply(ref.model(), ref.id());
        if (target == null) {
            String where = ref.model() == null ? "" : " in model '" + ref.model() + "'";
            throw ApiException.badRequest("Element '" + ownerId + "': reference '" + feature
                    + "' points to unknown element '" + ref.id() + "'" + where);
        }
        return target;
    }

    private static EClass resolveClass(String name, EReference containing, EPackage ePackage) {
        EClassifier classifier = ePackage.getEClassifier(name);
        if (classifier == null && containing != null) {
            // A contained object may belong to a subclass defined in the package of the reference type.
            classifier = containing.getEReferenceType().getEPackage().getEClassifier(name);
        }
        if (!(classifier instanceof EClass eClass)) {
            throw ApiException.badRequest("Unknown class '" + name + "' in metamodel " + ePackage.getNsURI());
        }
        return eClass;
    }

    private static EReference reference(EClass eClass, String name, boolean containment) {
        EStructuralFeature feature = eClass.getEStructuralFeature(name);
        if (!(feature instanceof EReference reference) || reference.isContainment() != containment
                || !MetamodelRegistry.isEditable(feature)) {
            throw ApiException.badRequest("Class " + eClass.getName() + " has no "
                    + (containment ? "containment" : "non-containment") + " reference '" + name + "'");
        }
        return reference;
    }

    private static void checkUpper(EReference reference, int size, String ownerId) {
        int upper = reference.getUpperBound();
        if (upper >= 0 && size > upper) {
            throw ApiException.badRequest("Element '" + ownerId + "': reference '" + reference.getName()
                    + "' accepts at most " + upper + " element(s)");
        }
    }

    private static void checkType(EReference reference, EObject target, String ownerId) {
        if (!reference.getEReferenceType().isInstance(target)) {
            throw ApiException.badRequest("Element '" + ownerId + "': reference '" + reference.getName()
                    + "' expects " + reference.getEReferenceType().getName() + " but got "
                    + target.eClass().getName());
        }
    }

    @SuppressWarnings("unchecked")
    private static void add(EObject owner, EReference reference, EObject value) {
        if (reference.isMany()) {
            ((EList<EObject>) owner.eGet(reference)).add(value);
        } else {
            owner.eSet(reference, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setAttribute(EObject object, EAttribute attribute, Object value, String ownerId) {
        EDataType dataType = attribute.getEAttributeType();
        try {
            if (attribute.isMany()) {
                if (!(value instanceof List<?> items)) {
                    throw new IllegalArgumentException("expected a list");
                }
                EList<Object> target = (EList<Object>) object.eGet(attribute);
                for (Object item : items) {
                    target.add(attributeFromJson(dataType, item));
                }
            } else if (value == null) {
                object.eUnset(attribute);
            } else {
                object.eSet(attribute, attributeFromJson(dataType, value));
            }
        } catch (RuntimeException e) {
            throw ApiException.badRequest("Element '" + ownerId + "': invalid value '" + value + "' for attribute '"
                    + attribute.getName() + "' of type " + dataType.getName());
        }
    }

    private static Object attributeFromJson(EDataType dataType, Object value) {
        String literal = String.valueOf(value);
        if (value instanceof Number number && "int".equals(MetamodelRegistry.primitiveName(dataType))) {
            if (number.doubleValue() != Math.rint(number.doubleValue())) {
                throw new IllegalArgumentException("not an integer");
            }
            literal = String.valueOf(number.longValue());
        }
        Object converted = EcoreUtil.createFromString(dataType, literal);
        if (converted == null) {
            throw new IllegalArgumentException("unknown literal");
        }
        return converted;
    }
}
