package org.modeling4genmusic.studio.generation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.generation.ExerciseGenerator.Availability;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Generates an exercise for a spec of the workspace and checks it against that spec. When the chosen
 * generator can give different answers, a rejected exercise is asked again, telling what was wrong,
 * and the best one is returned.
 */
@Service
public class GenerationService {

    private static final String SPEC_CLASS = "ExerciseSpec";

    private final WorkspaceStore store;
    private final List<ExerciseGenerator> generators;
    private final ExerciseValidator validator;
    private final GenerationProperties properties;

    public GenerationService(WorkspaceStore store, List<ExerciseGenerator> generators, ExerciseValidator validator,
            GenerationProperties properties) {
        this.store = store;
        this.generators = generators;
        this.validator = validator;
        this.properties = properties;
    }

    /** One exercise the generator wrote, with its checks. */
    public record Attempt(String abc, ValidationReport report) {
    }

    /**
     * @param abc      the chosen exercise
     * @param metadata facts about it, among which the generator, the number of attempts and the time taken
     * @param report   its checks
     * @param attempts every exercise that was written, in order; the chosen one is among them
     */
    public record Generation(String abc, Map<String, Object> metadata, ValidationReport report, List<Attempt> attempts) {
    }

    public record GeneratorInfo(String id, String label, String model, boolean available, String reason,
            boolean isDefault) {
    }

    public List<GeneratorInfo> generators() {
        return generators.stream().map(g -> {
            Availability a = g.availability();
            return new GeneratorInfo(g.id(), g.label(), a.model(), a.available(), a.reason(),
                    g.id().equals(properties.defaultGenerator()));
        }).toList();
    }

    public Generation generate(String workspaceId, String specModelId, String qualityModelId, String generatorId) {
        ExerciseGenerator generator = generator(generatorId);
        EObject spec = root(store.load(workspaceId, specModelId));
        if (spec == null || !SPEC_CLASS.equals(spec.eClass().getName())) {
            throw ApiException.badRequest("Il modello «" + store.meta(workspaceId, specModelId).name()
                    + "» non contiene una specifica di esercizio");
        }
        EObject quality = qualityModelId == null || qualityModelId.isBlank() ? null
                : root(store.load(workspaceId, qualityModelId));
        Availability availability = generator.availability();
        if (!availability.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, availability.reason());
        }

        int limit = generator.varies() ? Math.max(1, properties.maxAttempts()) : 1;
        long start = System.nanoTime();
        List<Attempt> attempts = new ArrayList<>();
        GenerationResult chosen = null;
        Attempt best = null;
        List<Check> rejected = List.of();
        for (int attempt = 1; attempt <= limit; attempt++) {
            GenerationResult result = generator.generate(spec, new GenerationContext(quality, attempt, rejected));
            Attempt current = new Attempt(result.abc(), validator.validate(result.abc(), spec, quality));
            attempts.add(current);
            // Fewer broken constraints first; with as many, the exercise closer to the quality goals.
            if (best == null || failures(current) < failures(best)
                    || (failures(current) == failures(best) && missedGoals(current) < missedGoals(best))) {
                best = current;
                chosen = result;
            }
            if (current.report().valid()) {
                break;
            }
            rejected = current.report().checks().stream().filter(c -> c.blocking() && !c.passed()).toList();
        }
        Map<String, Object> metadata = new LinkedHashMap<>(chosen.metadata());
        metadata.put("attempts", attempts.size());
        metadata.put("seconds", Math.round((System.nanoTime() - start) / 1e8) / 10.0);
        return new Generation(best.abc(), metadata, best.report(), attempts);
    }

    private static long failures(Attempt attempt) {
        return attempt.report().checks().stream().filter(c -> c.blocking() && !c.passed()).count();
    }

    /** Quality goals the exercise is not within; an exercise that cannot be read misses them all. */
    private static long missedGoals(Attempt attempt) {
        if (attempt.report().checks().stream().anyMatch(c -> "abc".equals(c.name()) && !c.passed())) {
            return Long.MAX_VALUE;
        }
        return attempt.report().checks().stream()
                .filter(c -> BasicExerciseValidator.QUALITY_GOAL.equals(c.name()) && !c.passed()).count();
    }

    /** The measures a dimension of a quality model can name. */
    public record MetricInfo(String name, String description) {
    }

    public List<MetricInfo> metrics() {
        return Metrics.all().stream().map(m -> new MetricInfo(m.name(), m.description())).toList();
    }

    private ExerciseGenerator generator(String id) {
        String wanted = id == null || id.isBlank() ? properties.defaultGenerator() : id.strip();
        return generators.stream().filter(g -> g.id().equals(wanted)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Generatore sconosciuto: " + wanted));
    }

    private static EObject root(XMLResource resource) {
        return resource.getContents().isEmpty() ? null : resource.getContents().get(0);
    }
}
