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
    private static final String NEEDS = "http://music/needs";
    private static final String CATALOG = "catalogo";
    private static final String EXERCISE = "http://music/exercise";
    private static final String QUALITY = "http://music/quality";
    private static final String QUALITY_MODEL = "qualita";
    private static final String WEAVING = "http://music/weaving";

    @TempDir
    Path workspace;

    private WorkspaceStore store;

    @BeforeEach
    void setUp() {
        StudioProperties properties = StudioProperties.of(Path.of("../metamodels"), workspace);
        store = new WorkspaceStore(properties, new MetamodelRegistry(properties), new ObjectMapper());
        saveTheCatalog();
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
                        List.of(need("n1", "t_vision", Map.of("p_scale", "1.5")),
                                need("n2", "t_span", Map.of("p_stretch", "9"))),
                        "patterns",
                        List.of(leaf("p1", "PatternMastery", Map.of("expression", "8. 16", "status", "LEARNING")))),
                Map.of("catalog", List.of(new Ref(CATALOG, "cat", null)))));
    }

    /** A need of the profile: a reference to its type in the catalog and one slot per given parameter. */
    private static ObjectNode need(String id, String type, Map<String, String> values) {
        List<ObjectNode> slots = values.entrySet().stream()
                .map(e -> node(id + "_" + e.getKey(), "Slot", Map.of("value", e.getValue()), null,
                        Map.of("parameter", List.of(new Ref(CATALOG, e.getKey(), null)))))
                .toList();
        return node(id, "Need", Map.of(), Map.of("slots", slots), Map.of("type", List.of(new Ref(CATALOG, type, null))));
    }

    /** The type level the profile refers to: two need types, each with a parameter and an effect. */
    private static List<ObjectNode> catalogRoots() {
        ObjectNode scale = leaf("p_scale", "ParameterDef",
                Map.of("name", "minStaffScale", "kind", "DECIMAL", "required", true, "min", 1.0));
        ObjectNode render = node("e_render", "ConstraintEffect", Map.of("constraintClass", "RenderConstraint"),
                Map.of("bindings", List.of(node("b_scale", "Binding", Map.of("feature", "staffScale"), null,
                        Map.of("parameter", List.of(new Ref(null, "p_scale", null)))))),
                null);
        ObjectNode stretch = leaf("p_stretch", "ParameterDef",
                Map.of("name", "maxStretchSemitones", "kind", "INTERVAL", "required", true));
        ObjectNode bars = node("e_bars", "SpecEffect", Map.of("feature", "bars", "combine", "MIN", "literal", "4"),
                null, null);
        return List.of(node("cat", "NeedCatalog", Map.of("name", "test"),
                Map.of("types",
                        List.of(node("t_vision", "NeedType", Map.of("name", "LowVision"),
                                Map.of("parameters", List.of(scale), "effects", List.of(render)), null),
                                node("t_span", "NeedType", Map.of("name", "HandSpanLimit"),
                                        Map.of("parameters", List.of(stretch), "effects", List.of(bars)), null))),
                null));
    }

    /** Saved before each test: the profiles refer to it. */
    private void saveTheCatalog() {
        ModelDocument saved = save(NEEDS, "Catalogo", null, catalogRoots());
        assertThat(saved.id()).isEqualTo(CATALOG);
        assertThat(store.get(WS, CATALOG).roots()).isEqualTo(catalogRoots());
        // The quality model the goals of the exercises refer to.
        assertThat(save(QUALITY, "Qualita", null, qualityRoots()).id()).isEqualTo(QUALITY_MODEL);
    }

    private static List<ObjectNode> exerciseRoots(String learnerModel) {
        ObjectNode pattern = leaf("pat", "Pattern", Map.of("kind", "RHYTHM", "expression", "8. 16"));
        ObjectNode contains = node("c1", "ContainsPattern",
                Map.of("strength", "SOFT", "weight", 1.0, "minOccurrences", 3), Map.of("pattern", List.of(pattern)),
                null);
        ObjectNode span = leaf("c2", "SpanConstraint", Map.of("strength", "HARD", "weight", 1.0, "hand", "RIGHT",
                "maxSimultaneousSemitones", 9, "maxStretchInPosition", 9));
        ObjectNode goal = node("g1", "QualityGoal", Map.of("level", "MEDIUM"), null,
                Map.of("dimension", List.of(new Ref(QUALITY_MODEL, "d1", null))));
        return List.of(node("spec", "ExerciseSpec",
                Map.of("instrument", "PIANO", "key", "G", "meter", "4/4", "bars", 8, "maxBpm", 0, "requestedBpm",
                        90),
                Map.of("constraints", List.of(contains, span), "qualityGoals", List.of(goal)),
                Map.of("learner", List.of(new Ref(learnerModel, "giulia", null)), "qualityModel",
                        List.of(new Ref(QUALITY_MODEL, "qm", null)))));
    }

    private static List<ObjectNode> qualityRoots() {
        ObjectNode band = leaf("r_med", "Band",
                Map.of("level", "MEDIUM", "min", 0.3, "max", 0.6, "hint", "Mix quarter notes with eighth notes."));
        ObjectNode dimension = node("d1", "QualityDimension",
                Map.of("name", "RhythmicComplexity", "label", "Complessità ritmica", "metric", "shortNoteShare",
                        "skillLimited", true, "skillArea", "RHYTHM", "implicit", true, "defaultLevel", "LOW"),
                Map.of("bands", List.of(band)), null);
        return List.of(node("qm", "QualityModel", Map.of("instrument", "PIANO"),
                Map.of("dimensions", List.of(dimension)), null));
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
