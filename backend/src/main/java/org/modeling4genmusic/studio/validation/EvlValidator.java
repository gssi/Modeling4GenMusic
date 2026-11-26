package org.modeling4genmusic.studio.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.evl.EvlModule;
import org.eclipse.epsilon.evl.dom.Constraint;
import org.eclipse.epsilon.evl.execute.FixInstance;
import org.eclipse.epsilon.evl.execute.UnsatisfiedConstraint;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the EVL scripts that {@code epsilon/validations.json} associates with a model. Nothing about the
 * domain is known here: scripts, model names and related models all come from that descriptor.
 */
@Component
public class EvlValidator {

    private final MetamodelRegistry registry;
    private final List<ValidationProfile> profiles;

    public EvlValidator(MetamodelRegistry registry, ObjectMapper json) {
        this.registry = registry;
        try (InputStream in = Scripts.open("validations.json")) {
            this.profiles = json.readValue(in, new TypeReference<List<ValidationProfile>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the validation descriptor", e);
        }
    }

    /** A diagnostic together with the live EVL result it comes from, needed to run its fixes. */
    public record Finding(Diagnostic diagnostic, UnsatisfiedConstraint unsatisfied) {

        /** Runs one of the fixes on the in-memory model. */
        public void applyFix(int fixIndex) {
            List<FixInstance> fixes = unsatisfied.getFixes();
            if (fixIndex < 0 || fixIndex >= fixes.size()) {
                throw new IndexOutOfBoundsException("No fix with index " + fixIndex);
            }
            try {
                fixes.get(fixIndex).perform();
            } catch (EolRuntimeException e) {
                throw new IllegalStateException("Fix failed: " + e.getMessage(), e);
            }
        }
    }

    public List<Finding> validate(String modelId, String metamodel, String role, XMLResource resource) {
        List<Finding> findings = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        for (ValidationProfile profile : profiles) {
            if (profile.appliesTo(metamodel, role)) {
                run(profile, modelId, resource, findings, usedIds);
            }
        }
        return findings;
    }

    private void run(ValidationProfile profile, String modelId, XMLResource resource, List<Finding> findings,
            Set<String> usedIds) {
        EvlModule module = new EvlModule();
        Scripts.parse(module, profile.script());
        module.getContext().getModelRepository().addModel(model(profile.model(), resource));
        if (profile.related() != null) {
            for (ValidationProfile.Related related : profile.related()) {
                module.getContext().getModelRepository().addModel(model(related.alias(), related(resource, related)));
            }
        }
        Scripts.publishTools(module, profile.tools());
        Set<UnsatisfiedConstraint> unsatisfied;
        try {
            unsatisfied = module.execute();
        } catch (EolRuntimeException e) {
            throw new IllegalStateException("Validation script " + profile.script() + " failed: " + e.getMessage(),
                    e);
        }
        List<Finding> current = new ArrayList<>();
        for (UnsatisfiedConstraint item : unsatisfied) {
            current.add(new Finding(diagnostic(modelId, resource, item, usedIds), item));
        }
        // EVL returns a set: sort by id so that the output is stable between runs.
        current.sort(Comparator.comparing(f -> f.diagnostic().id()));
        findings.addAll(current);
    }

    private static Diagnostic diagnostic(String modelId, XMLResource resource, UnsatisfiedConstraint item,
            Set<String> usedIds) {
        Constraint constraint = item.getConstraint();
        String elementId = item.getInstance() instanceof EObject element && element.eResource() == resource
                ? resource.getID(element)
                : null;
        Diagnostic.Severity severity = constraint.isInfo() ? Diagnostic.Severity.INFO
                : constraint.isCritique() ? Diagnostic.Severity.WARNING : Diagnostic.Severity.ERROR;
        List<Diagnostic.Fix> fixes = new ArrayList<>();
        for (FixInstance fix : item.getFixes()) {
            try {
                fixes.add(new Diagnostic.Fix(fix.getTitle()));
            } catch (EolRuntimeException e) {
                throw new IllegalStateException("Cannot compute a fix title: " + e.getMessage(), e);
            }
        }
        String id = Diagnostic.idOf(modelId, constraint.getName(), elementId);
        for (int i = 2; !usedIds.add(id); i++) {
            id = Diagnostic.idOf(modelId, constraint.getName() + "." + i, elementId);
        }
        return new Diagnostic(id, modelId, elementId, severity, constraint.getName(), item.getMessage(), fixes);
    }

    private InMemoryEmfModel model(String name, Resource resource) {
        return Scripts.model(name, resource, registry.packages());
    }

    /** Resource reached from the roots through the given reference, or an empty one when there is none. */
    private static Resource related(XMLResource resource, ValidationProfile.Related related) {
        for (EObject root : resource.getContents()) {
            EStructuralFeature feature = root.eClass().getEStructuralFeature(related.reference());
            if (feature == null) {
                continue;
            }
            Object value = root.eGet(feature);
            Object first = value instanceof List<?> list ? (list.isEmpty() ? null : list.get(0)) : value;
            if (first instanceof EObject target && !target.eIsProxy() && target.eResource() != null) {
                return target.eResource();
            }
        }
        return new XMIResourceImpl(URI.createURI("empty:" + related.alias()));
    }
}
