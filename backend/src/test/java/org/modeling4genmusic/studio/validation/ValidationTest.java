package org.modeling4genmusic.studio.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.model.ModelDocument.ObjectNode;
import org.modeling4genmusic.studio.model.ModelDocument.Ref;
import org.modeling4genmusic.studio.validation.Diagnostic.Severity;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;

import com.fasterxml.jackson.databind.ObjectMapper;

/** One satisfied and one violated case for every EVL constraint, plus the structural EMF checks. */
class ValidationTest {

    private static final String WS = "default";
    private static final String PROFILE = "http://music/profile";
    private static final String EXERCISE = "http://music/exercise";
    private static final String QUALITY = "http://music/quality";

    @TempDir
    Path workspace;

    private WorkspaceStore store;
    private ValidationService service;

    @BeforeEach
    void setUp() {
        StudioProperties properties = StudioProperties.of(Path.of("../metamodels"), workspace);
        MetamodelRegistry registry = new MetamodelRegistry(properties);
        ObjectMapper json = new ObjectMapper();
        store = new WorkspaceStore(properties, registry, json);
        service = new ValidationService(store, new EmfValidator(), new EvlValidator(registry, json));
    }

    // ---------------------------------------------------------------- builders

    private static ObjectNode node(String id, String eClass, Map<String, Object> attributes,
            Map<String, List<ObjectNode>> contents, Map<String, List<Ref>> references) {
        return new ObjectNode(id, eClass, attributes, contents, references);
    }

    private static ObjectNode leaf(String id, String eClass, Map<String, Object> attributes) {
        return node(id, eClass, attributes, null, null);
    }

    private String save(String metamodel, String name, String role, ObjectNode root) {
        ModelDocument created = store.create(WS, metamodel, name, role);
        store.replace(WS, created.id(), created.withRoots(List.of(root)));
        return created.id();
    }

    private static Map<String, Object> learnerAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", "giulia");
        attributes.put("instrument", "PIANO");
        attributes.put("comfortableBpm", 72);
        attributes.put("lowestMidi", 60);
        attributes.put("highestMidi", 79);
        return attributes;
    }

    private static ObjectNode learner(Map<String, Object> attributes, int rhythmLevel, double confidence,
            String learningPattern) {
        return node("giulia", "Learner", attributes,
                Map.of("skills",
                        List.of(leaf("s_rhythm", "Skill",
                                Map.of("area", "RHYTHM", "level", rhythmLevel, "confidence", confidence)),
                                leaf("s_pitch", "Skill",
                                        Map.of("area", "PITCH_READING", "level", 3, "confidence", 0.8))),
                        "patterns",
                        List.of(leaf("pm_quarter", "PatternMastery", Map.of("expression", "4", "status", "MASTERED")),
                                leaf("pm_dotted", "PatternMastery",
                                        Map.of("expression", learningPattern, "status", "LEARNING")),
                                leaf("pm_reverse", "PatternMastery",
                                        Map.of("expression", "16 8.", "status", "NOT_INTRODUCED")))),
                null);
    }

    private String giulia() {
        return save(PROFILE, "Giulia", null, learner(learnerAttributes(), 2, 0.7, "8. 16"));
    }

    private static ObjectNode patternConstraint(String id, String eClass, String expression) {
        return node(id, eClass, Map.of(),
                Map.of("pattern",
                        List.of(leaf(id + "_p", "Pattern", Map.of("kind", "RHYTHM", "expression", expression)))),
                null);
    }

    private static ObjectNode goal(String id, String attribute, String level) {
        return leaf(id, "QualityGoal", Map.of("attribute", attribute, "level", level));
    }

    private static ObjectNode intent(String learnerModel, int requestedBpm, int maxBpm, List<ObjectNode> constraints,
            List<ObjectNode> goals) {
        Map<String, List<ObjectNode>> contents = new LinkedHashMap<>();
        contents.put("constraints", constraints);
        contents.put("qualityGoals", goals);
        return node("intent", "ExerciseSpec",
                Map.of("key", "G", "meter", "4/4", "bars", 8, "requestedBpm", requestedBpm, "maxBpm", maxBpm),
                contents,
                learnerModel == null ? null : Map.of("learner", List.of(new Ref(learnerModel, "giulia", null))));
    }

    private static ObjectNode band(String id, String level, double min, double max) {
        return leaf(id, "Band", Map.of("level", level, "min", min, "max", max));
    }

    private static ObjectNode quality(List<ObjectNode> bands) {
        ObjectNode binding = node("b_rhythm", "MetricBinding",
                Map.of("attribute", "RHYTHMIC_COMPLEXITY", "metric", "rhythmScore"), Map.of("bands", bands), null);
        return node("qm", "QualityModel", Map.of("instrument", "PIANO"), Map.of("bindings", List.of(binding)), null);
    }

    private static List<String> names(List<Diagnostic> diagnostics) {
        return diagnostics.stream().map(Diagnostic::constraint).toList();
    }

    private static Diagnostic only(List<Diagnostic> diagnostics, String constraint) {
        List<Diagnostic> matching = diagnostics.stream().filter(d -> d.constraint().equals(constraint)).toList();
        assertThat(matching).as("diagnostics for %s in %s", constraint, diagnostics).hasSize(1);
        return matching.get(0);
    }

    // ---------------------------------------------------------------- profile.evl

    @Test
    void validProfileHasNoDiagnostics() {
        assertThat(service.validate(WS, giulia())).isEmpty();
    }

    @Test
    void nameRequired() {
        Map<String, Object> attributes = learnerAttributes();
        attributes.put("name", "   ");
        String id = save(PROFILE, "Blank", null, learner(attributes, 2, 0.7, "8. 16"));
        Diagnostic diagnostic = only(service.validate(WS, id), "NameRequired");
        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.elementId()).isEqualTo("giulia");
        assertThat(diagnostic.modelId()).isEqualTo(id);
        assertThat(diagnostic.id()).isEqualTo(id + "#NameRequired@giulia");
    }

    @Test
    void rangeOrdered() {
        Map<String, Object> attributes = learnerAttributes();
        attributes.put("lowestMidi", 79);
        attributes.put("highestMidi", 79);
        String id = save(PROFILE, "Range", null, learner(attributes, 2, 0.7, "8. 16"));
        assertThat(only(service.validate(WS, id), "RangeOrdered").severity()).isEqualTo(Severity.ERROR);
    }

    @Test
    void skillLevelBounds() {
        String level = save(PROFILE, "Level", null, learner(learnerAttributes(), 6, 0.7, "8. 16"));
        assertThat(only(service.validate(WS, level), "SkillLevelBounds").elementId()).isEqualTo("s_rhythm");
        String confidence = save(PROFILE, "Confidence", null, learner(learnerAttributes(), 2, 1.2, "8. 16"));
        assertThat(names(service.validate(WS, confidence))).containsExactly("SkillLevelBounds");
    }

    @Test
    void patternSyntax() {
        String id = save(PROFILE, "Syntax", null, learner(learnerAttributes(), 2, 0.7, "8. 3"));
        Diagnostic diagnostic = only(service.validate(WS, id), "PatternSyntax");
        assertThat(diagnostic.elementId()).isEqualTo("pm_dotted");
        assertThat(diagnostic.message()).contains("'3'");
    }

    @Test
    void patternWithoutExpressionIsReportedNotCrashed() {
        ObjectNode pattern = leaf("pm_empty", "PatternMastery", Map.of("status", "LEARNING"));
        ObjectNode learner = node("giulia", "Learner", learnerAttributes(), Map.of("patterns", List.of(pattern)), null);
        String id = save(PROFILE, "Empty pattern", null, learner);
        Diagnostic diagnostic = only(service.validate(WS, id), "PatternSyntax");
        assertThat(diagnostic.elementId()).isEqualTo("pm_empty");
        assertThat(diagnostic.message()).contains("vuoto");
    }

    // ---------------------------------------------------------------- intent.evl

    @Test
    void validIntentHasNoDiagnostics() {
        String id = save(EXERCISE, "Intento", "intent", intent(giulia(), 60, 0,
                List.of(patternConstraint("c1", "ContainsPattern", "8. 16"),
                        patternConstraint("c2", "ExcludesPattern", "16 8.")),
                List.of(goal("g1", "RHYTHMIC_COMPLEXITY", "MEDIUM"), goal("g2", "MELODIC_COMPLEXITY", "HIGH"))));
        assertThat(service.validate(WS, id)).isEmpty();
    }

    @Test
    void tempoAboveComfortIsFixable() {
        String id = save(EXERCISE, "Intento", null, intent(giulia(), 90, 0, List.of(), List.of()));
        Diagnostic diagnostic = only(service.validate(WS, id), "TempoAboveComfort");
        assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        assertThat(diagnostic.elementId()).isEqualTo("intent");
        assertThat(diagnostic.message()).contains("90").contains("72");
        assertThat(diagnostic.fixes()).extracting(Diagnostic.Fix::title).containsExactly("Limita il tempo a 72 bpm");

        ValidationService.FixResult result = service.applyFix(WS, id, diagnostic.id(), 0);
        assertThat(result.model().version()).isEqualTo(3);
        assertThat(result.model().roots().get(0).attributes()).containsEntry("maxBpm", 72)
                .containsEntry("requestedBpm", 90);
        assertThat(result.diagnostics()).isEmpty();
        assertThat(store.get(WS, id).roots().get(0).references().get("learner").get(0).unresolved()).isNull();
    }

    @Test
    void tempoAboveComfortStaysWhenTheLimitIsTooHigh() {
        String id = save(EXERCISE, "Intento", "intent", intent(giulia(), 90, 80, List.of(), List.of()));
        assertThat(names(service.validate(WS, id))).containsExactly("TempoAboveComfort");
    }

    @Test
    void containsExcludesConflict() {
        String id = save(EXERCISE, "Intento", "intent", intent(giulia(), 60, 0,
                List.of(patternConstraint("c1", "ContainsPattern", "8. 16"),
                        patternConstraint("c2", "ExcludesPattern", " 8.   16")),
                List.of()));
        Diagnostic diagnostic = only(service.validate(WS, id), "ContainsExcludesConflict");
        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.elementId()).isEqualTo("c1");
    }

    @Test
    void patternNotIntroduced() {
        String id = save(EXERCISE, "Intento", "intent",
                intent(giulia(), 60, 0, List.of(patternConstraint("c1", "ContainsPattern", "16 8.")), List.of()));
        Diagnostic diagnostic = only(service.validate(WS, id), "PatternNotIntroduced");
        assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        assertThat(diagnostic.elementId()).isEqualTo("c1");
    }

    @Test
    void complexityAboveLevel() {
        String id = save(EXERCISE, "Intento", "intent", intent(giulia(), 60, 0, List.of(),
                List.of(goal("g1", "RHYTHMIC_COMPLEXITY", "HIGH"), goal("g2", "DENSITY", "HIGH"))));
        Diagnostic diagnostic = only(service.validate(WS, id), "ComplexityAboveLevel");
        assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        assertThat(diagnostic.elementId()).isEqualTo("g1");
        assertThat(names(service.validate(WS, id))).hasSize(1);
    }

    @Test
    void intentRulesNeedALearnerAndSkipDerivedSpecs() {
        List<ObjectNode> goals = List.of(goal("g1", "RHYTHMIC_COMPLEXITY", "HIGH"));
        String noLearner = save(EXERCISE, "Senza learner", "intent", intent(null, 90, 0, List.of(), goals));
        assertThat(service.validate(WS, noLearner)).isEmpty();
        String derived = save(EXERCISE, "Derivata", "derived", intent(giulia(), 90, 0, List.of(), goals));
        assertThat(service.validate(WS, derived)).isEmpty();
    }

    // ---------------------------------------------------------------- quality.evl

    @Test
    void validQualityModelHasNoDiagnostics() {
        String id = save(QUALITY, "Piano", null, quality(
                List.of(band("r_med", "MEDIUM", 0.3, 0.6), band("r_low", "LOW", 0, 0.3), band("r_high", "HIGH", 0.6, 1))));
        assertThat(service.validate(WS, id)).isEmpty();
    }

    @Test
    void bandOrder() {
        String id = save(QUALITY, "Piano", null, quality(
                List.of(band("r_low", "LOW", 0, 0.3), band("r_med", "MEDIUM", 0.6, 0.3), band("r_high", "HIGH", 0.6, 1))));
        Diagnostic diagnostic = only(service.validate(WS, id), "BandOrder");
        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.elementId()).isEqualTo("r_med");
    }

    @Test
    void bandCoverage() {
        List<List<ObjectNode>> broken = new ArrayList<>();
        broken.add(List.of(band("r_low", "LOW", 0, 0.3), band("r_high", "HIGH", 0.6, 1))); // gap
        broken.add(List.of(band("r_low", "LOW", 0, 0.5), band("r_high", "HIGH", 0.4, 1))); // overlap
        broken.add(List.of(band("r_low", "LOW", 0.1, 0.5), band("r_high", "HIGH", 0.5, 1))); // starts late
        broken.add(List.of(band("r_low", "LOW", 0, 0.5), band("r_high", "HIGH", 0.5, 0.9))); // ends early
        broken.add(List.of());
        for (List<ObjectNode> bands : broken) {
            String id = save(QUALITY, "Piano", null, quality(bands));
            Diagnostic diagnostic = only(service.validate(WS, id), "BandCoverage");
            assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
            assertThat(diagnostic.elementId()).isEqualTo("b_rhythm");
        }
    }

    // ---------------------------------------------------------------- EMF structural validation

    @Test
    void missingRequiredAttributeAndContainment() {
        Map<String, Object> attributes = learnerAttributes();
        attributes.remove("name");
        String profile = save(PROFILE, "Senza nome", null, learner(attributes, 2, 0.7, "8. 16"));
        List<Diagnostic> diagnostics = service.validate(WS, profile);
        Diagnostic missing = only(diagnostics, "Multiplicity");
        assertThat(missing.severity()).isEqualTo(Severity.ERROR);
        assertThat(missing.elementId()).isEqualTo("giulia");
        assertThat(missing.message()).contains("'name'");
        assertThat(missing.id()).isEqualTo(profile + "#Multiplicity.name@giulia");
        assertThat(names(diagnostics)).contains("NameRequired");

        String intent = save(EXERCISE, "Intento", "intent", intent(null, 60, 0,
                List.of(node("c1", "ContainsPattern", Map.of(), null, null)), List.of()));
        Diagnostic noPattern = only(service.validate(WS, intent), "Multiplicity");
        assertThat(noPattern.elementId()).isEqualTo("c1");
        assertThat(noPattern.message()).contains("'pattern'");
    }

    @Test
    void danglingCrossModelReference() {
        String profile = giulia();
        String id = save(EXERCISE, "Intento", "intent", intent(profile, 90, 0, List.of(), List.of()));
        store.delete(WS, profile);
        List<Diagnostic> diagnostics = service.validate(WS, id);
        Diagnostic dangling = only(diagnostics, "UnresolvedReference");
        assertThat(dangling.severity()).isEqualTo(Severity.ERROR);
        assertThat(dangling.elementId()).isEqualTo("intent");
        assertThat(dangling.message()).contains("'learner'").contains(profile);
        assertThat(names(diagnostics)).containsExactly("UnresolvedReference");
    }
}
