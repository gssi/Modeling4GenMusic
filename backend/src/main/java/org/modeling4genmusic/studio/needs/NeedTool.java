package org.modeling4genmusic.studio.needs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;

/**
 * The generic part of the interpretation of a learner's needs, available to the scripts as the
 * {@code needs} variable. A need is typed by an element of a catalog model; the type says, by class and
 * feature name, what the need does to an exercise spec. Here those names are followed by reflection, so
 * that a type added to a catalog works without a change to the code.
 *
 * <p>Parameters are untyped because an unset attribute reaches a script call as null.
 */
public class NeedTool {

    // ---------------------------------------------------------------- values of a need

    /** What the learner's need says for a parameter of its type: the slot or else the default, or null. */
    public String parameterValue(Object need, Object parameter) {
        if (!(need instanceof EObject n) || !(parameter instanceof EObject p)) {
            return null;
        }
        for (EObject slot : children(n, "slots")) {
            if (slot.eGet(feature(slot, "parameter")) == p && !blank(text(slot, "value"))) {
                return text(slot, "value").strip();
            }
        }
        String fallback = text(p, "defaultValue");
        return blank(fallback) ? null : fallback.strip();
    }

    /** The slot of a need for a parameter, or null when the value comes from the default. */
    public EObject slotOf(Object need, Object parameter) {
        if (need instanceof EObject n) {
            for (EObject slot : children(n, "slots")) {
                if (slot.eGet(feature(slot, "parameter")) == parameter) {
                    return slot;
                }
            }
        }
        return null;
    }

    /** The text a binding or a spec effect stands for: the value of its parameter or its literal. */
    public String valueOf(Object need, Object value) {
        if (!(value instanceof EObject v)) {
            return null;
        }
        Object parameter = v.eGet(feature(v, "parameter"));
        if (parameter != null) {
            return parameterValue(need, parameter);
        }
        String literal = text(v, "literal");
        return blank(literal) ? null : literal;
    }

    /** Replaces {@code {name}} in a rationale. */
    public String fill(Object template, Object name, Object value) {
        return template == null ? "" : template.toString().replace("{" + name + "}", value == null ? "?" : value.toString());
    }

    /**
     * The constraints of one class that the needs of a learner lead to, each as feature name to text.
     * It lets a script reason about a limit without knowing which type of need it comes from.
     */
    public List<Map<String, String>> constraints(Object learner, Object constraintClass) {
        List<Map<String, String>> result = new ArrayList<>();
        if (!(learner instanceof EObject l) || l.eIsProxy()) {
            return result;
        }
        for (EObject need : children(l, "needs")) {
            Object type = need.eGet(feature(need, "type"));
            if (!(type instanceof EObject t) || t.eIsProxy()) {
                continue;
            }
            for (EObject effect : children(t, "effects")) {
                if (!effect.eClass().getName().equals("ConstraintEffect")
                        || !String.valueOf(constraintClass).equals(text(effect, "constraintClass"))) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (EObject binding : children(effect, "bindings")) {
                    String value = valueOf(need, binding);
                    if (value != null) {
                        values.put(text(binding, "feature"), value);
                    }
                }
                result.add(values);
            }
        }
        return result;
    }

    /**
     * The bounds the needs of a learner put on attributes of the spec, in the order of the profile:
     * each with the name of the need type, the feature, the value, how it combines and the rationale
     * with the values of this learner in place of the parameter names.
     */
    public List<Map<String, String>> limits(Object learner) {
        List<Map<String, String>> result = new ArrayList<>();
        if (!(learner instanceof EObject l) || l.eIsProxy()) {
            return result;
        }
        for (EObject need : children(l, "needs")) {
            Object type = need.eGet(feature(need, "type"));
            if (!(type instanceof EObject t) || t.eIsProxy()) {
                continue;
            }
            for (EObject effect : children(t, "effects")) {
                String value = effect.eClass().getName().equals("SpecEffect") ? valueOf(need, effect) : null;
                if (value == null) {
                    continue;
                }
                String rationale = blank(text(effect, "rationale")) ? text(t, "name") : text(effect, "rationale");
                for (EObject parameter : children(t, "parameters")) {
                    rationale = fill(rationale, text(parameter, "name"), parameterValue(need, parameter));
                }
                Map<String, String> limit = new LinkedHashMap<>();
                limit.put("type", text(t, "name"));
                limit.put("feature", text(effect, "feature"));
                limit.put("value", value);
                limit.put("combine", literal(effect, "combine"));
                limit.put("rationale", rationale);
                result.add(limit);
            }
        }
        return result;
    }

    /**
     * Whether a bound would change a value: with MIN the value is above the ceiling, with MAX below the
     * floor, with SET different. Texts that are not numbers are compared as they are.
     */
    public boolean exceeds(Object current, Object bound, Object combine) {
        if (current == null || bound == null) {
            return false;
        }
        String c = current.toString().strip();
        String b = bound.toString().strip();
        try {
            double have = Double.parseDouble(c);
            double want = Double.parseDouble(b);
            return switch (String.valueOf(combine)) {
                case "MAX" -> have < want;
                case "SET" -> have != want;
                default -> have > want;
            };
        } catch (NumberFormatException e) {
            return !c.equals(b);
        }
    }

    // ---------------------------------------------------------------- writing the spec

    /**
     * Sets a feature of an element from its text form. A dotted path goes through single contained
     * elements, creating the missing ones, which are returned so that the script can trace them.
     */
    public List<EObject> set(Object target, Object path, Object value) {
        List<EObject> created = new ArrayList<>();
        EObject current = (EObject) target;
        String[] steps = String.valueOf(path).split("\\.");
        for (int i = 0; i < steps.length - 1; i++) {
            EStructuralFeature step = current.eClass().getEStructuralFeature(steps[i]);
            if (!(step instanceof EReference reference) || !reference.isContainment() || reference.isMany()) {
                throw new IllegalArgumentException("'" + steps[i] + "' is not a single contained element of "
                        + current.eClass().getName());
            }
            EObject child = (EObject) current.eGet(reference);
            if (child == null) {
                child = EcoreUtil.create(reference.getEReferenceType());
                current.eSet(reference, child);
                created.add(child);
            }
            current = child;
        }
        EAttribute attribute = attribute(current.eClass(), steps[steps.length - 1]);
        current.eSet(attribute, convert(attribute, value == null ? null : value.toString()));
        return created;
    }

    /** The text form of a feature of an element, or null when it has no value. */
    public String read(Object target, Object featureName) {
        EObject element = (EObject) target;
        EAttribute attribute = attribute(element.eClass(), String.valueOf(featureName));
        Object value = element.eGet(attribute);
        return value == null || !element.eIsSet(attribute) ? null
                : EcoreUtil.convertToString(attribute.getEAttributeType(), value);
    }

    /**
     * Bounds a numeric feature: with MIN the value is a ceiling, with MAX a floor, with SET it replaces.
     * A feature without a value takes the given one. Returns whether the element changed.
     */
    public boolean limit(Object target, Object featureName, Object value, Object combine) {
        EObject element = (EObject) target;
        EAttribute attribute = attribute(element.eClass(), String.valueOf(featureName));
        Object wanted = convert(attribute, value == null ? null : value.toString());
        Object current = element.eGet(attribute);
        boolean replace = !element.eIsSet(attribute) || "SET".equals(String.valueOf(combine));
        if (!replace && wanted instanceof Number w && current instanceof Number c) {
            replace = "MAX".equals(String.valueOf(combine)) ? w.doubleValue() > c.doubleValue()
                    : w.doubleValue() < c.doubleValue();
        }
        if (!replace || wanted.equals(current)) {
            return false;
        }
        element.eSet(attribute, wanted);
        return true;
    }

    // ---------------------------------------------------------------- checks for the scripts

    /** Why a text is not a value of a parameter, or null when it is one. */
    public String problemOf(Object parameter, Object value) {
        EObject p = (EObject) parameter;
        String kind = literal(p, "kind");
        String text = value == null ? "" : value.toString().strip();
        if (text.isEmpty()) {
            return "manca il valore";
        }
        switch (kind) {
            case "HAND":
                return text.equals("LEFT") || text.equals("RIGHT") ? null : "deve essere LEFT o RIGHT";
            case "RHYTHM":
                return RhythmPattern.errorOf(text);
            default:
                break;
        }
        double number;
        try {
            number = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return "«" + text + "» non è un numero";
        }
        if (!kind.equals("DECIMAL") && number != Math.rint(number)) {
            return "«" + text + "» non è un numero intero";
        }
        Object min = p.eGet(feature(p, "min"));
        Object max = p.eGet(feature(p, "max"));
        if (min instanceof Number m && number < m.doubleValue()) {
            return "il valore " + text + " è sotto il minimo ammesso (" + plain(m) + ")";
        }
        if (max instanceof Number m && number > m.doubleValue()) {
            return "il valore " + text + " è sopra il massimo ammesso (" + plain(m) + ")";
        }
        return null;
    }

    /**
     * Why a class of a metamodel cannot be the target of an effect, or null when it can: it must exist,
     * be concrete and, when a supertype is given, be one of its kinds.
     */
    public String classProblem(Object context, Object nsUri, Object className, Object superClassName) {
        EClass eClass = eClass(context, nsUri, className);
        if (eClass == null) {
            return "la classe «" + className + "» non esiste nel metamodello " + nsUri;
        }
        if (eClass.isAbstract()) {
            return "la classe «" + className + "» è astratta: non se ne possono creare elementi";
        }
        if (superClassName != null && eClass.getEAllSuperTypes().stream()
                .noneMatch(s -> s.getName().equals(superClassName.toString()))) {
            return "la classe «" + className + "» non è un " + superClassName;
        }
        return null;
    }

    /**
     * Why a value cannot be written to a feature of a class, or null when it can. The value is a
     * binding or a spec effect: its parameter must be of a kind the feature accepts, its literal must
     * be readable as the type of the feature.
     */
    public String bindingProblem(Object context, Object nsUri, Object className, Object path, Object value) {
        EClass eClass = eClass(context, nsUri, className);
        if (eClass == null) {
            return null; // reported by classProblem
        }
        String[] steps = String.valueOf(path).split("\\.");
        for (int i = 0; i < steps.length - 1; i++) {
            EStructuralFeature step = eClass.getEStructuralFeature(steps[i]);
            if (!(step instanceof EReference reference) || !reference.isContainment() || reference.isMany()) {
                return "«" + steps[i] + "» non è un elemento contenuto singolo di " + eClass.getName();
            }
            eClass = reference.getEReferenceType();
        }
        String name = steps[steps.length - 1];
        if (!(eClass.getEStructuralFeature(name) instanceof EAttribute attribute) || attribute.isMany()) {
            return "«" + name + "» non è un attributo di " + eClass.getName();
        }
        EObject v = (EObject) value;
        Object parameter = v.eGet(feature(v, "parameter"));
        if (parameter instanceof EObject p) {
            String kind = literal(p, "kind");
            return accepts(attribute.getEAttributeType(), kind) ? null
                    : "l'attributo «" + name + "» di " + eClass.getName() + " non accetta un parametro di tipo " + kind;
        }
        String literal = text(v, "literal");
        if (blank(literal)) {
            return "manca il valore: serve un parametro oppure un letterale";
        }
        try {
            convert(attribute, literal);
            return null;
        } catch (IllegalArgumentException e) {
            return "«" + literal + "» non è un valore dell'attributo «" + name + "» di " + eClass.getName();
        }
    }

    /** Whether a feature of a class holds a number, so that it can be bounded. */
    public boolean isNumeric(Object context, Object nsUri, Object className, Object featureName) {
        EClass eClass = eClass(context, nsUri, className);
        return eClass != null && eClass.getEStructuralFeature(String.valueOf(featureName)) instanceof EAttribute a
                && numeric(a.getEAttributeType());
    }

    // ---------------------------------------------------------------- reflection

    private static boolean accepts(EDataType type, String kind) {
        return switch (kind) {
            case "INTEGER", "INTERVAL", "NOTE" -> numeric(type);
            case "DECIMAL" -> decimal(type);
            case "HAND" -> type instanceof EEnum e && e.getEEnumLiteral("LEFT") != null
                    && e.getEEnumLiteral("RIGHT") != null;
            default -> type.getInstanceClass() == String.class;
        };
    }

    private static boolean decimal(EDataType type) {
        Class<?> c = type.getInstanceClass();
        return c == double.class || c == Double.class || c == float.class || c == Float.class;
    }

    private static boolean numeric(EDataType type) {
        Class<?> c = type.getInstanceClass();
        return decimal(type) || c == int.class || c == Integer.class || c == long.class || c == Long.class
                || c == short.class || c == Short.class;
    }

    private static Object convert(EAttribute attribute, String text) {
        EDataType type = attribute.getEAttributeType();
        if (text == null) {
            throw new IllegalArgumentException("No value for " + attribute.getName());
        }
        Object value;
        try {
            value = EcoreUtil.createFromString(type, text.strip());
        } catch (RuntimeException e) {
            value = null;
        }
        if (value == null) {
            throw new IllegalArgumentException("'" + text + "' is not a value of " + attribute.getName());
        }
        return value;
    }

    private static EAttribute attribute(EClass eClass, String name) {
        if (eClass.getEStructuralFeature(name) instanceof EAttribute attribute && !attribute.isMany()) {
            return attribute;
        }
        throw new IllegalArgumentException("'" + name + "' is not an attribute of " + eClass.getName());
    }

    /** A class of a metamodel known to the resource set of the given element. */
    private static EClass eClass(Object context, Object nsUri, Object className) {
        if (!(context instanceof EObject element) || element.eResource() == null
                || element.eResource().getResourceSet() == null || className == null) {
            return null;
        }
        EPackage ePackage = element.eResource().getResourceSet().getPackageRegistry()
                .getEPackage(String.valueOf(nsUri));
        EClassifier classifier = ePackage == null ? null : ePackage.getEClassifier(className.toString());
        return classifier instanceof EClass eClass ? eClass : null;
    }

    private static EStructuralFeature feature(EObject element, String name) {
        EStructuralFeature feature = element.eClass().getEStructuralFeature(name);
        if (feature == null) {
            throw new IllegalArgumentException(element.eClass().getName() + " has no feature '" + name + "'");
        }
        return feature;
    }

    @SuppressWarnings("unchecked")
    private static List<EObject> children(EObject element, String name) {
        return (List<EObject>) element.eGet(feature(element, name));
    }

    private static String text(EObject element, String name) {
        Object value = element.eGet(feature(element, name));
        return value == null ? null : value.toString();
    }

    private static String literal(EObject element, String name) {
        return String.valueOf(element.eGet(feature(element, name)));
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String plain(Number number) {
        double value = number.doubleValue();
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
