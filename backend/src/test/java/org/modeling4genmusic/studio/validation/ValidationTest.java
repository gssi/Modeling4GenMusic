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
        service = new ValidationService(store, new EmfValidator(), new EvlValidator(registry, json, new RuleStore(properties)));
        try {
            store.importXmi(WS, "piano.quality.xmi", java.nio.file.Files.readAllBytes(Path.of("../fixtures/giulia/piano.quality.xmi")),
                    null, null);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
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

    private static final String PIANO = "piano.quality";

    /** A goal on a dimension of the quality model of the fixtures, which {@link #setUp()} imports. */
    private static ObjectNode goal(String id, String dimension, String level) {
        return goal(id, PIANO, dimension, level);
    }

    private static ObjectNode goal(String id, String qualityModel, String dimension, String level) {
        return node(id, "QualityGoal", Map.of("level", level), null,
                Map.of("dimension", List.of(new Ref(qualityModel, dimension, null))));
    }

    private static ObjectNode intent(String learnerModel, int requestedBpm, int maxBpm, List<ObjectNode> constraints,
            List<ObjectNode> goals) {
        Map<String, List<ObjectNode>> contents = new LinkedHashMap<>();
        contents.put("constraints", constraints);
        contents.put("qualityGoals", goals);
        return node("intent", "ExerciseSpec",
                Map.of("key", "G", "meter", "4/4", "bars", 8, "requestedBpm", requestedBpm, "maxBpm", maxBpm),
                contents,
                references(learnerModel, goals.isEmpty() ? null : PIANO));
    }

    private static Map<String, List<Ref>> references(String learnerModel, String qualityModel) {
        Map<String, List<Ref>> references = new LinkedHashMap<>();
        if (learnerModel != null) {
            references.put("learner", List.of(new Ref(learnerModel, "giulia", null)));
        }
        if (qualityModel != null) {
            references.put("qualityModel", List.of(new Ref(qualityModel, "piano_quality", null)));
        }
        return references;
    }

    private static ObjectNode band(String id, String level, double min, double max) {
        return leaf(id, "Band", Map.of("level", level, "min", min, "max", max));
    }

    private static ObjectNode quality(List<ObjectNode> bands) {
        ObjectNode dimension = node("b_rhythm", "QualityDimension",
                Map.of("name", "RhythmicComplexity", "metric", "shortNoteShare"), Map.of("bands", bands), null);
        return node("qm", "QualityModel", Map.of("instrument", "PIANO"), Map.of("dimensions", List.of(dimension)), null);
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
                List.of(goal("g1", "RhythmicComplexity", "MEDIUM"), goal("g2", "MelodicComplexity", "HIGH"))));
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
                List.of(goal("g1", "RhythmicComplexity", "HIGH"), goal("g2", "Density", "HIGH"))));
        Diagnostic diagnostic = only(service.validate(WS, id), "ComplexityAboveLevel");
        assertThat(diagnostic.severity()).isEqualTo(Severity.WARNING);
        assertThat(diagnostic.elementId()).isEqualTo("g1");
        assertThat(names(service.validate(WS, id))).hasSize(1);
    }

    @Test
    void intentRulesNeedALearnerAndSkipDerivedSpecs() {
        List<ObjectNode> goals = List.of(goal("g1", "RhythmicComplexity", "HIGH"));
        String noLearner = save(EXERCISE, "Senza learner", "intent", intent(null, 90, 0, List.of(), goals));
        assertThat(service.validate(WS, noLearner)).isEmpty();
        String derived = save(EXERCISE, "Derivata", "derived", intent(giulia(), 90, 0, List.of(), goals));
        assertThat(service.validate(WS, derived)).isEmpty();
    }

    @Test
    void goalsNeedADimensionOfTheQualityModelOfTheIntent() {
        assertThat(service.validate(WS, PIANO)).isEmpty();
        String other = save(QUALITY, "Altro", null, quality(
                List.of(band("o_low", "LOW", 0, 0.5), band("o_high", "HIGH", 0.5, 1))));

        // The goal uses a dimension of a quality model that is not the one of the intent.
        String foreign = save(EXERCISE, "Altra dimensione", "intent", intent(giulia(), 60, 0, List.of(),
                List.of(goal("g1", other, "b_rhythm", "LOW"))));
        Diagnostic diagnostic = only(service.validate(WS, foreign), "GoalDimension");
        assertThat(diagnostic.severity()).isEqualTo(Severity.ERROR);
        assertThat(diagnostic.elementId()).isEqualTo("g1");

        // Goals without a quality model have nothing that says what they mean.
        ObjectNode spec = intent(giulia(), 60, 0, List.of(), List.of(goal("g1", "Density", "LOW")));
        String orphan = save(EXERCISE, "Senza modello", "intent", new ObjectNode(spec.id(), spec.eClass(), spec.attributes(),
                spec.contents(), references(giulia(), null)));
        assertThat(names(service.validate(WS, orphan))).containsExactly("QualityModelForGoals");

        String twice = save(EXERCISE, "Due volte", "intent", intent(giulia(), 60, 0, List.of(),
                List.of(goal("g1", "Density", "LOW"), goal("g2", "Density", "HIGH"))));
        assertThat(names(service.validate(WS, twice))).containsExactly("OneGoalPerDimension", "OneGoalPerDimension");
    }

    // ---------------------------------------------------------------- quality.evl

    @Test
    void aQualityModelSaysWhatItsDimensionsAre() {
        ObjectNode one = node("d1", "QualityDimension", Map.of("name", "Density", "metric", "noteDensity", "implicit", true,
                "defaultLevel", "HIGH"), Map.of("bands", List.of(band("a", "LOW", 0, 1), band("b", "LOW", 0, 1))), null);
        ObjectNode two = node("d2", "QualityDimension", Map.of("name", "Density", "metric", "noteDensity"),
                Map.of("bands", List.of(band("c", "LOW", 0, 1))), null);
        String id = save(QUALITY, "Doppio", null, node("qm", "QualityModel", Map.of(), Map.of("dimensions", List.of(one, two)), null));
        assertThat(names(service.validate(WS, id))).containsExactlyInAnyOrder("UniqueDimensionNames", "BandCoverage",
                "OneBandPerLevel", "DefaultLevelHasABand");

        // A metric is a measure the studio can take: a name it does not know is refused, with the ones it knows.
        ObjectNode unknown = node("d1", "QualityDimension", Map.of("name", "Swing", "label", "Swing", "metric", "swingFeel"),
                Map.of("bands", List.of(band("a", "LOW", 0, 1))), null);
        String strange = save(QUALITY, "Strano", null, node("qm", "QualityModel", Map.of(), Map.of("dimensions", List.of(unknown)), null));
        Diagnostic metric = only(service.validate(WS, strange), "KnownMetric");
        assertThat(metric.severity()).isEqualTo(Severity.ERROR);
        assertThat(metric.message()).isEqualTo("Swing: la metrica «swingFeel» non esiste. Quelle disponibili: "
                + "shortNoteShare, rhythmVariety, meanLeap, rangeUse, noteDensity");
    }


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

    // ---------------------------------------------------------------- physical limits

    private static final String CATALOG = "default.needs";

    /** The catalog of the fixtures, where the type of a hand span limit and its bounds are defined. */
    private void importCatalog() throws java.io.IOException {
        if (!store.exists(WS, CATALOG)) {
            store.importXmi(WS, "default.needs.xmi",
                    java.nio.file.Files.readAllBytes(Path.of("../fixtures/giulia/default.needs.xmi")), null, null);
        }
    }

    /** A profile with one hand span limit: parameter name to value, null for a profile without needs. */
    private String profileWithReach(String name, Map<String, String> limit) throws java.io.IOException {
        importCatalog();
        Map<String, List<ObjectNode>> contents = null;
        if (limit != null) {
            List<ObjectNode> slots = limit.entrySet().stream()
                    .map(e -> node("slot_" + e.getKey(), "Slot", Map.of("value", e.getValue()), null,
                            Map.of("parameter", List.of(new Ref(CATALOG, "HandSpanLimit_" + e.getKey(), null)))))
                    .toList();
            contents = Map.of("needs", List.of(node("need_handspan", "Need", Map.of(), Map.of("slots", slots),
                    Map.of("type", List.of(new Ref(CATALOG, "HandSpanLimit", null))))));
        }
        return save(PROFILE, name, null, node("giulia", "Learner", learnerAttributes(), contents,
                Map.of("catalog", List.of(new Ref(CATALOG, "needs", null)))));
    }

    private static ObjectNode leap(String expression) {
        return node("c_leap", "ContainsPattern", Map.of(),
                Map.of("pattern", List.of(leaf("p_leap", "Pattern", Map.of("kind", "INTERVAL", "expression", expression)))),
                null);
    }

    @Test
    void theCatalogOfTheFixturesIsSound() throws Exception {
        importCatalog();
        assertThat(service.validate(WS, CATALOG)).isEmpty();
    }

    @Test
    void needValuesAreCheckedAgainstTheirType() throws Exception {
        assertThat(service.validate(WS, profileWithReach("Left hand", Map.of("hand", "LEFT", "maxStretchSemitones", "9"))))
                .isEmpty();
        assertThat(service.validate(WS, profileWithReach("Measured",
                Map.of("hand", "RIGHT", "handSpanCm", "16.0", "maxStretchSemitones", "24")))).isEmpty();
        // A parameter without a slot takes the default of the catalog.
        assertThat(service.validate(WS, profileWithReach("Defaults", Map.of()))).isEmpty();

        Diagnostic narrow = only(service.validate(WS,
                profileWithReach("Too narrow", Map.of("hand", "RIGHT", "maxStretchSemitones", "1"))), "SlotValue");
        assertThat(narrow.severity()).isEqualTo(Diagnostic.Severity.ERROR);
        assertThat(narrow.elementId()).isEqualTo("slot_maxStretchSemitones");
        assertThat(narrow.message()).isEqualTo(
                "Limite di apertura della mano, apertura massima: il valore 1 è sotto il minimo ammesso (2)");
        assertThat(only(service.validate(WS, profileWithReach("Too wide", Map.of("maxStretchSemitones", "25"))),
                "SlotValue").message()).contains("sopra il massimo ammesso (24)");
        assertThat(only(service.validate(WS, profileWithReach("Negative span",
                Map.of("handSpanCm", "-1.0", "maxStretchSemitones", "9"))), "SlotValue").message())
                .contains("misura in cm").contains("-1.0");
        assertThat(only(service.validate(WS, profileWithReach("Half a semitone", Map.of("maxStretchSemitones", "9.5"))),
                "SlotValue").message()).contains("non è un numero intero");
        assertThat(only(service.validate(WS, profileWithReach("Third hand", Map.of("hand", "MIDDLE"))), "SlotValue")
                .message()).contains("LEFT o RIGHT");
    }

    @Test
    void needsWithoutACatalogOrOfAnotherCatalogAreErrors() throws Exception {
        importCatalog();
        ObjectNode need = node("n", "Need", Map.of(), null,
                Map.of("type", List.of(new Ref(CATALOG, "LowVision", null))));
        String orphan = save(PROFILE, "No catalog", null,
                node("giulia", "Learner", learnerAttributes(), Map.of("needs", List.of(need)), null));
        assertThat(names(service.validate(WS, orphan))).containsExactly("CatalogForNeeds");

        // A slot for a parameter of another type says nothing about this need.
        ObjectNode stray = node("s", "Slot", Map.of("value", "4"), null,
                Map.of("parameter", List.of(new Ref(CATALOG, "ShortAttentionSpan_maxBars", null))));
        ObjectNode mixed = node("n", "Need", Map.of(), Map.of("slots", List.of(stray)),
                Map.of("type", List.of(new Ref(CATALOG, "LowVision", null))));
        String id = save(PROFILE, "Stray slot", null, node("giulia", "Learner", learnerAttributes(),
                Map.of("needs", List.of(mixed)), Map.of("catalog", List.of(new Ref(CATALOG, "needs", null)))));
        assertThat(only(service.validate(WS, id), "SlotOfTheType").elementId()).isEqualTo("s");
    }

    @Test
    void aCatalogThatCannotBeAppliedIsRefused() {
        ObjectNode scale = leaf("p", "ParameterDef", Map.of("name", "scale", "kind", "DECIMAL", "min", 3.0, "max", 1.0));
        ObjectNode hand = leaf("h", "ParameterDef", Map.of("name", "hand", "kind", "HAND", "defaultValue", "BOTH"));
        Map<String, List<Ref>> byScale = Map.of("parameter", List.of(new Ref(null, "p", null)));
        ObjectNode unknownClass = node("e1", "ConstraintEffect", Map.of("constraintClass", "PedalConstraint"), null, null);
        ObjectNode abstractClass = node("e2", "ConstraintEffect", Map.of("constraintClass", "Constraint"), null, null);
        ObjectNode notAConstraint = node("e3", "ConstraintEffect", Map.of("constraintClass", "Pattern"), null, null);
        ObjectNode render = node("e4", "ConstraintEffect", Map.of("constraintClass", "RenderConstraint"),
                Map.of("bindings", List.of(node("b1", "Binding", Map.of("feature", "zoom"), null, byScale),
                        node("b2", "Binding", Map.of("feature", "staffScale"), null,
                                Map.of("parameter", List.of(new Ref(null, "h", null)))),
                        node("b3", "Binding", Map.of("feature", "staffScale", "literal", "big"), null, null),
                        node("b4", "Binding", Map.of("feature", "staffScale"), null, null),
                        node("b5", "Binding", Map.of("feature", "staffScale"), null, byScale))),
                null);
        ObjectNode key = node("e5", "SpecEffect", Map.of("feature", "key", "combine", "MIN", "literal", "C"), null, null);
        ObjectNode bars = node("e6", "SpecEffect", Map.of("feature", "bars", "combine", "MIN", "literal", "4"), null, null);
        String id = save("http://music/needs", "Broken catalog", null, node("cat", "NeedCatalog", Map.of("name", "x"),
                Map.of("types", List.of(node("t", "NeedType", Map.of("name", "Odd"),
                        Map.of("parameters", List.of(scale, hand), "effects",
                                List.of(unknownClass, abstractClass, notAConstraint, render, key, bars)),
                        null))),
                null));

        List<Diagnostic> diagnostics = service.validate(WS, id);
        assertThat(diagnostics).extracting(d -> d.constraint() + "@" + d.elementId()).containsExactlyInAnyOrder(
                "BoundsOrdered@p", "DefaultIsAValue@h", "KnownConstraintClass@e1", "KnownConstraintClass@e2",
                "KnownConstraintClass@e3", "BindableFeature@b1", "BindableFeature@b2", "BindableFeature@b3",
                "BindableFeature@b4", "BoundOnANumber@e5");
        assertThat(diagnostics).allMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        assertThat(only(diagnostics, "BoundOnANumber").message()).contains("Odd").contains("«key»");
        assertThat(diagnostics.stream().filter(d -> "b2".equals(d.elementId())).findFirst().orElseThrow().message())
                .contains("non accetta un parametro di tipo HAND");
    }

    @Test
    void leapBeyondReach() throws Exception {
        String reach = profileWithReach("Reach", Map.of("hand", "RIGHT", "maxStretchSemitones", "9"));
        String fine = save(EXERCISE, "Sixth", "intent", intent(reach, 60, 0, List.of(leap("+9 -4")), List.of()));
        assertThat(names(service.validate(WS, fine))).doesNotContain("LeapBeyondReach");

        String octave = save(EXERCISE, "Octave", "intent", intent(reach, 60, 0, List.of(leap("+3 -12")), List.of()));
        Diagnostic diagnostic = only(service.validate(WS, octave), "LeapBeyondReach");
        assertThat(diagnostic.severity()).isEqualTo(Diagnostic.Severity.WARNING);
        assertThat(diagnostic.elementId()).isEqualTo("c_leap");
        assertThat(diagnostic.fixes()).isEmpty();
        assertThat(diagnostic.message()).contains("12 semitoni (ottava)").contains("cambio di posizione");

        // Only the right hand counts, and without a limit there is nothing to compare with.
        String leftOnly = profileWithReach("Left only", Map.of("hand", "LEFT", "maxStretchSemitones", "5"));
        String noLimit = profileWithReach("No limit", null);
        for (String profile : List.of(leftOnly, noLimit)) {
            String id = save(EXERCISE, "Octave for " + profile, "intent",
                    intent(profile, 60, 0, List.of(leap("+12")), List.of()));
            assertThat(names(service.validate(WS, id))).doesNotContain("LeapBeyondReach");
        }
    }

    // ---------------------------------------------------------------- weaving.evl

    @Test
    void handWrittenTraceWithoutLinksGetsWarningsInsteadOfTheCompletenessError() {
        String intentId = save(EXERCISE, "Trace intent", "intent", intent(null, 60, 0, List.of(), List.of()));
        Ref intent = new Ref(intentId, "intent", null);
        ObjectNode weaving = node("w", "DerivationWeaving", Map.of("name", "test"), null,
                Map.of("teacherIntent", List.of(intent), "derivedSpec", List.of(intent)));
        String id = save("http://music/weaving", "Hand trace", null, weaving);

        List<Diagnostic> diagnostics = service.validate(WS, id);
        assertThat(names(diagnostics)).contains("TraceWithoutLinks", "DerivedSpecIsTheIntent")
                .doesNotContain("DerivedElementTraced");
        assertThat(only(diagnostics, "TraceWithoutLinks").severity()).isEqualTo(Diagnostic.Severity.WARNING);
    }
}
