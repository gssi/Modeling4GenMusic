package org.modeling4genmusic.studio.derivation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.etl.EtlModule;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.validation.Diagnostic;
import org.modeling4genmusic.studio.validation.ValidationService;
import org.modeling4genmusic.studio.workspace.ModelMeta;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the ETL transformation described by {@code epsilon/derivation.json} on models of the workspace
 * and saves what it produces. Which models go in and out, and under which names, comes from the descriptor.
 */
@Service
public class DerivationService {

    private final WorkspaceStore store;
    private final MetamodelRegistry registry;
    private final ValidationService validation;
    private final DerivationProfile profile;

    public DerivationService(WorkspaceStore store, MetamodelRegistry registry, ValidationService validation,
            ObjectMapper json) {
        this.store = store;
        this.registry = registry;
        this.validation = validation;
        try (InputStream in = Scripts.open("derivation.json")) {
            this.profile = json.readValue(in, DerivationProfile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the derivation descriptor", e);
        }
    }

    /** Ids of the produced models by response field, plus the diagnostics worth showing. */
    public record Result(Map<String, String> outputs, List<Diagnostic> diagnostics) {
    }

    /**
     * @param parameters request fields; each input of the descriptor reads its model id from one of them
     */
    public synchronized Result derive(String workspaceId, Map<String, String> parameters) {
        Map<DerivationProfile.Input, String> inputIds = new LinkedHashMap<>();
        for (DerivationProfile.Input input : profile.inputs()) {
            String id = parameters == null ? null : parameters.get(input.parameter());
            if (id == null || id.isBlank()) {
                throw ApiException.badRequest("Manca il campo " + input.parameter());
            }
            ModelMeta meta = store.meta(workspaceId, id);
            if (!input.metamodel().equals(meta.metamodel())) {
                throw ApiException.badRequest("Il modello «" + meta.name() + "» non è del tipo atteso per "
                        + input.parameter());
            }
            inputIds.put(input, id);
        }

        List<Diagnostic> residual = new ArrayList<>();
        for (var entry : inputIds.entrySet()) {
            if (!entry.getKey().validated()) {
                continue;
            }
            List<Diagnostic> diagnostics = validation.validate(workspaceId, entry.getValue());
            long errors = diagnostics.stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).count();
            if (errors > 0) {
                String name = store.meta(workspaceId, entry.getValue()).name();
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Il modello «" + name + "» ha "
                        + (errors == 1 ? "un errore" : errors + " errori") + ": va corretto prima di derivare.");
            }
            if (entry.getKey().primary()) {
                residual.addAll(diagnostics);
            }
        }

        String primaryId = inputIds.get(profile.primary());
        String primaryName = store.meta(workspaceId, primaryId).name();

        ResourceSet session = store.newSession();
        EtlModule module = new EtlModule();
        Scripts.parse(module, profile.script());
        inputIds.forEach((input, id) -> module.getContext().getModelRepository()
                .addModel(Scripts.model(input.alias(), store.loadIn(session, workspaceId, id), registry.packages())));
        Map<DerivationProfile.Output, XMLResource> produced = new LinkedHashMap<>();
        Map<String, String> outputIds = new LinkedHashMap<>();
        for (DerivationProfile.Output output : profile.outputs()) {
            String id = primaryId + "." + output.idSuffix();
            XMLResource resource = store.createIn(session, workspaceId, id);
            produced.put(output, resource);
            outputIds.put(output.result(), id);
            module.getContext().getModelRepository()
                    .addModel(Scripts.model(output.alias(), resource, registry.packages()));
        }
        Map<String, Object> tools = Scripts.publishTools(module, profile.tools());

        try {
            module.execute();
        } catch (EolRuntimeException e) {
            // A script stops with `throw` when its inputs cannot be transformed: the reason is for the user.
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Derivazione non riuscita: " + e.getReason());
        }
        for (Object tool : tools.values()) {
            if (tool instanceof DerivationHook hook) {
                hook.afterDerivation();
            }
        }

        produced.forEach((output, resource) -> store.put(workspaceId, outputIds.get(output.result()), resource,
                output.name().formatted(primaryName), output.metamodel(), output.role()));

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (DerivationProfile.Output output : profile.outputs()) {
            if (output.validated()) {
                diagnostics.addAll(validation.validate(workspaceId, outputIds.get(output.result())));
            }
        }
        diagnostics.addAll(residual);
        return new Result(outputIds, diagnostics);
    }
}
