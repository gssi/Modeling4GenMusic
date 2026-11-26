package org.modeling4genmusic.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.model.ModelDocument.ObjectNode;
import org.modeling4genmusic.studio.model.ModelDocument.Ref;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;

import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON -> EMF -> XMI -> EMF -> JSON must give back the same model, for each of the four metamodels. */
class RoundTripTest {

    private static final String WS = "default";
    private static final String PROFILE = "http://music/profile";
    private static final String EXERCISE = "http://music/exercise";
    private static final String QUALITY = "http://music/quality";
    private static final String WEAVING = "http://music/weaving";

    @TempDir
    Path workspace;

    private WorkspaceStore store;

    @BeforeEach
    void setUp() {
        StudioProperties properties = StudioProperties.of(Path.of("../metamodels"), workspace);
        store = new WorkspaceStore(properties, new MetamodelRegistry(properties), new ObjectMapper());
    }

    private static ObjectNode node(String id, String eClass, Map<String, Object> attributes,
            Map<String, List<ObjectNode>> contents, Map<String, List<Ref>> references) {
        return new ObjectNode(id, eClass, attributes, contents, references);
    }

    private static ObjectNode leaf(String id, String eClass, Map<String, Object> attributes) {
        return node(id, eClass, attributes, null, null);
    }

    private ModelDocument save(String metamodel, String name, String role, List<ObjectNode> roots) {
        ModelDocument created = store.create(WS, metamodel, name, role);
        return store.replace(WS, created.id(), created.withRoots(roots));
    }

    private static List<ObjectNode> profileRoots() {
        return List.of(node("giulia", "Learner",
                Map.of("name", "giulia", "instrument", "PIANO", "comfortableBpm", 72, "lowestMidi", 60,
                        "highestMidi", 79),
                Map.of("skills",
                        List.of(leaf("s1", "Skill", Map.of("area", "RHYTHM", "level", 2, "confidence", 0.7)),
                                leaf("s2", "Skill",
                                        Map.of("area", "PITCH_READING", "level", 3, "confidence", 0.8))),
                        "needs",
                        List.of(leaf("n1", "LowVision", Map.of("minStaffScale", 1.5)),
                                leaf("n2", "HandSpanLimit",
                                        Map.of("hand", "RIGHT", "handSpanCm", 16.0, "maxStretchSemitones", 9)),
                                leaf("n3", "HandSpanLimit",
                                        Map.of("hand", "LEFT", "handSpanCm", 0.0, "maxStretchSemitones", 10))),
                        "patterns",
                        List.of(leaf("p1", "PatternMastery", Map.of("expression", "8. 16", "status", "LEARNING")))),
                null));
    }

    private static List<ObjectNode> exerciseRoots(String learnerModel) {
        ObjectNode pattern = leaf("pat", "Pattern", Map.of("kind", "RHYTHM", "expression", "8. 16"));
        ObjectNode contains = node("c1", "ContainsPattern",
                Map.of("strength", "SOFT", "weight", 1.0, "minOccurrences", 3), Map.of("pattern", List.of(pattern)),
                null);
        ObjectNode span = leaf("c2", "SpanConstraint", Map.of("strength", "HARD", "weight", 1.0, "hand", "RIGHT",
                "maxSimultaneousSemitones", 9, "maxStretchInPosition", 9));
        ObjectNode goal = leaf("g1", "QualityGoal", Map.of("attribute", "RHYTHMIC_COMPLEXITY", "level", "MEDIUM"));
        return List.of(node("spec", "ExerciseSpec",
                Map.of("instrument", "PIANO", "key", "G", "meter", "4/4", "bars", 8, "maxBpm", 0, "requestedBpm",
                        90),
                Map.of("constraints", List.of(contains, span), "qualityGoals", List.of(goal)),
                Map.of("learner", List.of(new Ref(learnerModel, "giulia", null)))));
    }

    private static List<ObjectNode> qualityRoots() {
        ObjectNode band = leaf("r_med", "Band", Map.of("level", "MEDIUM", "min", 0.3, "max", 0.6));
        ObjectNode binding = node("b1", "MetricBinding",
                Map.of("attribute", "RHYTHMIC_COMPLEXITY", "metric", "rhythmScore"), Map.of("bands", List.of(band)),
                null);
        return List.of(node("qm", "QualityModel", Map.of("instrument", "PIANO"),
                Map.of("bindings", List.of(binding)), null));
    }

    @Test
    void profileRoundTrip() {
        ModelDocument saved = save(PROFILE, "Profilo Giulia", null, profileRoots());
        assertThat(saved.id()).isEqualTo("profilo-giulia");
        assertThat(saved.version()).isEqualTo(2);
        assertThat(saved.roots()).isEqualTo(profileRoots());
        assertThat(store.get(WS, saved.id()).roots()).isEqualTo(profileRoots());
    }

    @Test
    void exerciseRoundTripKeepsCrossModelReference() {
        ModelDocument profile = save(PROFILE, "Profilo Giulia", null, profileRoots());
        ModelDocument intent = save(EXERCISE, "Intento", "intent", exerciseRoots(profile.id()));
        assertThat(intent.role()).isEqualTo("intent");
        assertThat(store.get(WS, intent.id()).roots()).isEqualTo(exerciseRoots(profile.id()));
        String xmi = new String(store.exportXmi(WS, intent.id()), StandardCharsets.UTF_8);
        assertThat(xmi).contains("href=\"profilo-giulia.xmi#giulia\"").contains("xmi:id=\"spec\"");
    }

    @Test
    void qualityRoundTrip() {
        ModelDocument saved = save(QUALITY, "Piano", null, qualityRoots());
        assertThat(store.get(WS, saved.id()).roots()).isEqualTo(qualityRoots());
    }

    @Test
    void weavingRoundTripReferencesElementsOfAnyModel() {
        ModelDocument profile = save(PROFILE, "Profilo Giulia", null, profileRoots());
        ModelDocument intent = save(EXERCISE, "Intento", "intent", exerciseRoots(profile.id()));
        ModelDocument derived = save(EXERCISE, "Spec derivata", "derived", exerciseRoots(profile.id()));
        ModelDocument quality = save(QUALITY, "Piano", null, qualityRoots());

        ObjectNode source = node("src1", "SourceEnd", Map.of("origin", "TEACHER", "feature", "requestedBpm"), null,
                Map.of("element", List.of(new Ref(intent.id(), "spec", null))));
        ObjectNode fix = node("l1", "ConflictFix",
                Map.of("rule", "TempoLimit", "rationale", "Il tempo richiesto supera quello comodo",
                        "targetFeature", "maxBpm", "critique", "TempoAboveComfort", "originalValue", "90",
                        "newValue", "72"),
                Map.of("sources", List.of(source)),
                Map.of("target", List.of(new Ref(derived.id(), "spec", null))));
        ObjectNode resolutionSource = node("src2", "SourceEnd", Map.of("origin", "QUALITY_MODEL"), null,
                Map.of("element", List.of(new Ref(quality.id(), "r_med", null))));
        ObjectNode resolution = node("l2", "Resolution", Map.of("rule", "ResolveThresholds"),
                Map.of("sources", List.of(resolutionSource)),
                Map.of("target", List.of(new Ref(derived.id(), "g1", null))));
        List<ObjectNode> roots = List.of(node("w", "DerivationWeaving", Map.of("name", "giulia"),
                Map.of("links", List.of(fix, resolution)),
                Map.of("learner", List.of(new Ref(profile.id(), "giulia", null)), "teacherIntent",
                        List.of(new Ref(intent.id(), "spec", null)), "qualityModel",
                        List.of(new Ref(quality.id(), "qm", null)), "derivedSpec",
                        List.of(new Ref(derived.id(), "spec", null)))));

        ModelDocument weaving = save(WEAVING, "Weaving", null, roots);
        assertThat(store.get(WS, weaving.id()).roots()).isEqualTo(roots);
    }

    @Test
    void rejectsStaleVersionUnknownFeatureAndDanglingReference() {
        ModelDocument created = store.create(WS, PROFILE, "Profilo", null);
        store.replace(WS, created.id(), created.withRoots(profileRoots()));
        assertThatThrownBy(() -> store.replace(WS, created.id(), created.withRoots(profileRoots())))
                .isInstanceOf(ApiException.class).hasMessageContaining("modified elsewhere");

        ModelDocument current = store.get(WS, created.id());
        List<ObjectNode> unknown = List.of(leaf("x", "Learner", Map.of("name", "x", "shoeSize", 42)));
        assertThatThrownBy(() -> store.replace(WS, current.id(), current.withRoots(unknown)))
                .isInstanceOf(ApiException.class).hasMessageContaining("shoeSize");

        ModelDocument intent = store.create(WS, EXERCISE, "Intento", "intent");
        assertThatThrownBy(() -> store.replace(WS, intent.id(), intent.withRoots(exerciseRoots("missing"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("unknown element");
    }

    @Test
    void danglingReferenceIsReportedAsUnresolved() {
        ModelDocument profile = save(PROFILE, "Profilo Giulia", null, profileRoots());
        ModelDocument intent = save(EXERCISE, "Intento", "intent", exerciseRoots(profile.id()));
        store.delete(WS, profile.id());
        Ref learner = store.get(WS, intent.id()).roots().get(0).references().get("learner").get(0);
        assertThat(learner).isEqualTo(new Ref(profile.id(), "giulia", true));
    }
}
