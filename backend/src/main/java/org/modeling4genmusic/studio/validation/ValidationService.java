package org.modeling4genmusic.studio.validation;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.xmi.XMLResource;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.workspace.ModelMeta;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.stereotype.Service;

/** Structural (EMF) plus rule-based (EVL) validation of a workspace model, and application of fixes. */
@Service
public class ValidationService {

    private final WorkspaceStore store;
    private final EmfValidator emfValidator;
    private final EvlValidator evlValidator;

    public ValidationService(WorkspaceStore store, EmfValidator emfValidator, EvlValidator evlValidator) {
        this.store = store;
        this.emfValidator = emfValidator;
        this.evlValidator = evlValidator;
    }

    public record FixResult(ModelDocument model, List<Diagnostic> diagnostics) {
    }

    public List<Diagnostic> validate(String workspaceId, String modelId) {
        ModelMeta meta = store.meta(workspaceId, modelId);
        XMLResource resource = store.load(workspaceId, modelId);
        List<Diagnostic> diagnostics = new ArrayList<>(emfValidator.validate(modelId, resource));
        evlValidator.validate(modelId, meta.metamodel(), meta.role(), resource)
                .forEach(finding -> diagnostics.add(finding.diagnostic()));
        return diagnostics;
    }

    /**
     * Stateless fix: validation is run again, the diagnostic is found by its deterministic id, the fix is
     * executed on the freshly loaded model, which is then saved as a new version and validated once more.
     */
    public synchronized FixResult applyFix(String workspaceId, String modelId, String diagnosticId, int fixIndex) {
        ModelMeta meta = store.meta(workspaceId, modelId);
        XMLResource resource = store.load(workspaceId, modelId);
        EvlValidator.Finding finding = evlValidator.validate(modelId, meta.metamodel(), meta.role(), resource)
                .stream().filter(f -> f.diagnostic().id().equals(diagnosticId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Diagnostic no longer present: " + diagnosticId));
        if (fixIndex < 0 || fixIndex >= finding.diagnostic().fixes().size()) {
            throw ApiException.badRequest("Diagnostic " + diagnosticId + " has no fix with index " + fixIndex);
        }
        finding.applyFix(fixIndex);
        ModelDocument saved = store.save(workspaceId, modelId, resource);
        return new FixResult(saved, validate(workspaceId, modelId));
    }
}
