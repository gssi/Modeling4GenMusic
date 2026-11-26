package org.modeling4genmusic.studio.generation;

import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.stereotype.Service;

/** Generates an exercise for a spec of the workspace and checks it against that spec. */
@Service
public class GenerationService {

    private static final String SPEC_CLASS = "ExerciseSpec";

    private final WorkspaceStore store;
    private final ExerciseGenerator generator;
    private final ExerciseValidator validator;

    public GenerationService(WorkspaceStore store, ExerciseGenerator generator, ExerciseValidator validator) {
        this.store = store;
        this.generator = generator;
        this.validator = validator;
    }

    public record Generation(String abc, Map<String, Object> metadata, ValidationReport report) {
    }

    public Generation generate(String workspaceId, String specModelId, String qualityModelId) {
        EObject spec = root(store.load(workspaceId, specModelId));
        if (spec == null || !SPEC_CLASS.equals(spec.eClass().getName())) {
            throw ApiException.badRequest("Il modello «" + store.meta(workspaceId, specModelId).name()
                    + "» non contiene una specifica di esercizio");
        }
        EObject quality = qualityModelId == null || qualityModelId.isBlank() ? null
                : root(store.load(workspaceId, qualityModelId));
        GenerationResult result = generator.generate(spec, new GenerationContext(quality));
        return new Generation(result.abc(), result.metadata(), validator.validate(result.abc(), spec, quality));
    }

    private static EObject root(XMLResource resource) {
        return resource.getContents().isEmpty() ? null : resource.getContents().get(0);
    }
}
