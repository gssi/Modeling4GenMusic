package org.modeling4genmusic.studio.validation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.Diagnostician;
import org.eclipse.emf.ecore.util.EObjectValidator;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.modeling4genmusic.studio.model.ModelJsonMapper;
import org.springframework.stereotype.Component;

/** Structural validation driven by the metamodel alone: multiplicities, data values, dangling references. */
@Component
public class EmfValidator {

    public List<Diagnostic> validate(String modelId, XMLResource resource) {
        List<Diagnostic> result = new ArrayList<>();
        for (EObject root : resource.getContents()) {
            collect(Diagnostician.INSTANCE.validate(root), modelId, resource, result);
        }
        return result;
    }

    private void collect(org.eclipse.emf.common.util.Diagnostic diagnostic, String modelId, XMLResource resource,
            List<Diagnostic> result) {
        if (!diagnostic.getChildren().isEmpty()) {
            diagnostic.getChildren().forEach(child -> collect(child, modelId, resource, result));
            return;
        }
        if (diagnostic.getSeverity() == org.eclipse.emf.common.util.Diagnostic.OK || diagnostic.getData().isEmpty()
                || !(diagnostic.getData().get(0) instanceof EObject element)) {
            return;
        }
        EStructuralFeature feature = diagnostic.getData().size() > 1
                && diagnostic.getData().get(1) instanceof EStructuralFeature f ? f : null;
        String featureName = feature == null ? null : feature.getName();
        boolean structural = EObjectValidator.DIAGNOSTIC_SOURCE.equals(diagnostic.getSource());
        String constraint = structural ? constraintName(diagnostic.getCode()) : "EmfConstraint";
        String message = structural ? message(diagnostic, element, feature) : diagnostic.getMessage();
        String elementId = resource.getID(element);
        String qualified = featureName == null ? constraint : constraint + "." + featureName;
        result.add(new Diagnostic(Diagnostic.idOf(modelId, qualified, elementId), modelId, elementId,
                severity(diagnostic.getSeverity()), constraint, message, List.of()));
    }

    private static Diagnostic.Severity severity(int severity) {
        if (severity >= org.eclipse.emf.common.util.Diagnostic.ERROR) {
            return Diagnostic.Severity.ERROR;
        }
        return severity == org.eclipse.emf.common.util.Diagnostic.WARNING ? Diagnostic.Severity.WARNING
                : Diagnostic.Severity.INFO;
    }

    private static String constraintName(int code) {
        return switch (code) {
            case EObjectValidator.EOBJECT__EVERY_MULTIPCITY_CONFORMS -> "Multiplicity";
            case EObjectValidator.EOBJECT__EVERY_DATA_VALUE_CONFORMS -> "DataValue";
            case EObjectValidator.EOBJECT__EVERY_REFERENCE_IS_CONTAINED -> "DanglingReference";
            case EObjectValidator.EOBJECT__EVERY_PROXY_RESOLVES -> "UnresolvedReference";
            default -> "EmfConstraint";
        };
    }

    private static String message(org.eclipse.emf.common.util.Diagnostic diagnostic, EObject element,
            EStructuralFeature feature) {
        String name = feature == null ? "?" : feature.getName();
        switch (diagnostic.getCode()) {
            case EObjectValidator.EOBJECT__EVERY_MULTIPCITY_CONFORMS: {
                if (feature == null) {
                    break;
                }
                int size = feature.isMany() ? ((List<?>) element.eGet(feature)).size()
                        : element.eIsSet(feature) ? 1 : 0;
                if (size < feature.getLowerBound()) {
                    return feature.isMany() || feature.getLowerBound() > 1
                            ? "'" + name + "' richiede almeno " + feature.getLowerBound() + " elemento/i (presenti: "
                                    + size + ")"
                            : "Il campo obbligatorio '" + name + "' non è valorizzato";
                }
                return "'" + name + "' ammette al massimo " + feature.getUpperBound() + " elemento/i (presenti: "
                        + size + ")";
            }
            case EObjectValidator.EOBJECT__EVERY_PROXY_RESOLVES:
            case EObjectValidator.EOBJECT__EVERY_REFERENCE_IS_CONTAINED: {
                String target = "";
                if (diagnostic.getData().size() > 2 && diagnostic.getData().get(2) instanceof EObject referenced) {
                    URI uri = EcoreUtil.getURI(referenced);
                    if (uri != null && uri.fragment() != null) {
                        target = " ('" + uri.fragment() + "' nel modello '" + ModelJsonMapper.modelIdOf(uri) + "')";
                    }
                }
                return "Il riferimento '" + name + "' punta a un elemento che non esiste" + target;
            }
            default:
                break;
        }
        return diagnostic.getMessage();
    }
}
