package org.modeling4genmusic.studio.derivation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.model.ModelDocument.ObjectNode;
import org.modeling4genmusic.studio.model.ModelDocument.Ref;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Acceptance of the derivation milestone on the Giulia fixtures. */
@SpringBootTest
@AutoConfigureMockMvc
class DerivationApiTest {

    private static final String WS = "default";
    private static final String DERIVE = """
            {"learnerModelId":"giulia.profile","teacherIntentModelId":"giulia.intent","qualityModelId":"piano.quality"}""";

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("studio.workspace-dir", () -> workspace.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    WorkspaceStore store;

    @Autowired
    ObjectMapper json;

    @BeforeEach
    void importFixtures() throws Exception {
        for (String id : new String[] { "giulia.intent.weaving", "giulia.intent.derived", "giulia.intent",
                "giulia.profile", "piano.quality", "default.needs" }) {
            if (store.exists(WS, id)) {
                store.delete(WS, id);
            }
        }
        for (String file : new String[] { "default.needs.xmi", "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : "")).andExpect(status().isCreated());
        }
    }

    private static List<ObjectNode> all(ObjectNode node, List<ObjectNode> into) {
        into.add(node);
        node.contents().values().forEach(children -> children.forEach(child -> all(child, into)));
        return into;
    }

    private static ObjectNode byClass(List<ObjectNode> nodes, String eClass) {
        List<ObjectNode> matching = nodes.stream().filter(n -> n.eClass().equals(eClass)).toList();
        assertThat(matching).as(eClass).hasSize(1);
        return matching.get(0);
    }

    @Test
    void giuliaDerivationMatchesTheExpectedSpecAndWeaving() throws Exception {
        mvc.perform(post("/api/models/giulia.intent/fixes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosticId\":\"giulia.intent#TempoAboveComfort@intent\",\"fixIndex\":0}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derivedSpecModelId").value("giulia.intent.derived"))
                .andExpect(jsonPath("$.weavingModelId").value("giulia.intent.weaving"))
                .andExpect(jsonPath("$.diagnostics", hasSize(0)));

        // ---- derived spec
        ModelDocument derived = store.get(WS, "giulia.intent.derived");
        assertThat(derived.role()).isEqualTo("derived");
        assertThat(derived.name()).isEqualTo("Spec derivata da giulia.intent");
        ObjectNode spec = derived.roots().get(0);
        assertThat(spec.id()).isEqualTo("spec");
        assertThat(spec.attributes()).containsEntry("instrument", "PIANO").containsEntry("key", "G")
                .containsEntry("meter", "4/4").containsEntry("bars", 8).containsEntry("maxBpm", 72)
                .containsEntry("requestedBpm", 90);
        assertThat(spec.references().get("learner")).containsExactly(new Ref("giulia.profile", "giulia", null));

        List<ObjectNode> constraints = spec.contents().get("constraints");
        assertThat(constraints).extracting(ObjectNode::id).containsExactly("c_contains_1", "c_excludes_1",
                "c_range_1", "c_render_1", "c_span_1");
        ObjectNode contains = byClass(constraints, "ContainsPattern");
        assertThat(contains.attributes()).containsEntry("strength", "HARD").containsEntry("minOccurrences", 3);
        assertThat(contains.contents().get("pattern").get(0).attributes()).containsEntry("expression", "8. 16")
                .containsEntry("kind", "RHYTHM");
        ObjectNode excludes = byClass(constraints, "ExcludesPattern");
        assertThat(excludes.attributes()).containsEntry("strength", "HARD");
        assertThat(excludes.contents().get("pattern").get(0).attributes()).containsEntry("expression", "16 8.");
        assertThat(byClass(constraints, "RangeConstraint").attributes()).containsEntry("strength", "HARD")
                .containsEntry("lowestMidi", 60).containsEntry("highestMidi", 79);
        assertThat(byClass(constraints, "RenderConstraint").attributes()).containsEntry("strength", "HARD")
                .containsEntry("staffScale", 1.5);

        assertThat(byClass(constraints, "SpanConstraint").attributes()).containsEntry("strength", "HARD")
                .containsEntry("hand", "RIGHT").containsEntry("maxSimultaneousSemitones", 9)
                .containsEntry("maxStretchInPosition", 9);

        List<ObjectNode> goals = spec.contents().get("qualityGoals");
        assertThat(goals).extracting(ObjectNode::id).containsExactly("qg_rhythmic_complexity",
                "qg_melodic_complexity");
        // A goal names a dimension of the quality model; the second one is implicit in that model.
        assertThat(goals.get(0).attributes()).containsEntry("level", "MEDIUM");
        assertThat(goals.get(0).references().get("dimension").get(0)).isEqualTo(new Ref("piano.quality", "RhythmicComplexity", null));
        assertThat(goals.get(1).attributes()).containsEntry("level", "LOW");
        assertThat(goals.get(1).references().get("dimension").get(0).id()).isEqualTo("MelodicComplexity");
        assertThat(spec.references().get("qualityModel").get(0)).isEqualTo(new Ref("piano.quality", "piano_quality", null));

        // ---- weaving
        ObjectNode weaving = store.get(WS, "giulia.intent.weaving").roots().get(0);
        assertThat(weaving.references()).containsEntry("learner", List.of(new Ref("giulia.profile", "giulia", null)))
                .containsEntry("teacherIntent", List.of(new Ref("giulia.intent", "intent", null)))
                .containsEntry("qualityModel", List.of(new Ref("piano.quality", "piano_quality", null)))
                .containsEntry("needCatalog", List.of(new Ref("default.needs", "needs", null)))
                .containsEntry("derivedSpec", List.of(new Ref("giulia.intent.derived", "spec", null)));
        List<ObjectNode> links = weaving.contents().get("links");
        assertThat(links).allSatisfy(link -> {
            assertThat(link.attributes().get("rule")).asString().isNotBlank();
            assertThat(link.attributes().get("rationale")).asString().isNotBlank();
            assertThat(link.contents().get("sources")).isNotEmpty();
            assertThat(link.references().get("target")).hasSize(1);
        });

        List<ObjectNode> fixes = links.stream().filter(l -> l.eClass().equals("ConflictFix")).toList();
        assertThat(fixes).hasSize(1);
        assertThat(fixes.get(0).attributes()).containsEntry("rule", "TempoLimit")
                .containsEntry("critique", "TempoAboveComfort").containsEntry("originalValue", "90")
                .containsEntry("newValue", "72").containsEntry("targetFeature", "maxBpm");

        List<String> traced = links.stream().map(l -> l.references().get("target").get(0).id()).toList();
        assertThat(all(spec, new ArrayList<>())).extracting(ObjectNode::id).isSubsetOf(traced);

        List<ObjectNode> resolutions = links.stream().filter(l -> l.eClass().equals("Resolution")).toList();
        assertThat(resolutions).hasSize(2);
        assertThat(resolutions).extracting(l -> l.references().get("target").get(0).id() + " -> "
                + l.contents().get("sources").get(0).references().get("element").get(0).id())
                .containsExactly("qg_rhythmic_complexity -> r_med", "qg_melodic_complexity -> m_low");

        assertThat(links).extracting(l -> l.attributes().get("rule")).contains("CopyIntent",
                "InstrumentFromLearner", "TempoLimit", "StrengthenLearningPattern", "ExcludeNotIntroduced",
                "RangeFromLearner", "ApplyNeeds", "CopyQualityGoals",
                "ApplyImplicitGoals", "ResolveThresholds");
        // The span constraint is not written by a rule about hands: it comes from the type of the need.
        ObjectNode span = links.stream().filter(l -> l.references().get("target").get(0).id().equals("c_span_1"))
                .findFirst().orElseThrow();
        assertThat(span.eClass()).isEqualTo("Derivation");
        assertThat(span.attributes()).containsEntry("rule", "ApplyNeeds").containsEntry("rationale",
                "Limite di apertura della mano destra: massimo 9 semitoni (sesta maggiore)");
        List<ObjectNode> spanSources = span.contents().get("sources");
        assertThat(spanSources).extracting(source -> source.attributes().get("origin") + " "
                + source.references().get("element").get(0).model() + "#"
                + source.references().get("element").get(0).id()).containsExactly(
                        "PROFILE giulia.profile#need_handspan_hand",
                        "PROFILE giulia.profile#need_handspan_maxStretchSemitones",
                        "PROFILE giulia.profile#need_handspan_maxStretchSemitones",
                        "NEED_CATALOG default.needs#HandSpanLimit_span");
        assertThat(spanSources.get(0).attributes()).containsEntry("feature", "value");

        mvc.perform(post("/api/models/giulia.intent.weaving/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        // The derived spec is not an intent: the intent rules do not apply to it.
        mvc.perform(post("/api/models/giulia.intent.derived/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void anOctaveLeapIsWarnedAboutAndTheSpecStillGetsTheSpanConstraint() throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia/giulia-leap.intent.xmi"));
        if (store.exists(WS, "giulia-leap.intent")) {
            store.delete(WS, "giulia-leap.intent");
        }
        mvc.perform(multipart("/api/models/import")
                .file(new MockMultipartFile("file", "giulia-leap.intent.xmi", "text/xml", bytes)).param("role", "intent"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/models/giulia-leap.intent/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].severity").value("WARNING"))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].elementId").value("c_contains_leap"))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].fixes[*]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].message").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("12 semitoni (ottava)"),
                                org.hamcrest.Matchers.containsString("9 semitoni, sesta maggiore"),
                                org.hamcrest.Matchers.containsString("cambio di posizione")))));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON)
                .content(DERIVE.replace("\"giulia.intent\"", "\"giulia-leap.intent\"")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.derivedSpecModelId").value("giulia-leap.intent.derived"))
                .andExpect(jsonPath("$.diagnostics[*].constraint",
                        org.hamcrest.Matchers.containsInAnyOrder("LeapBeyondReach", "TempoAboveComfort")));
        List<ObjectNode> constraints = store.get(WS, "giulia-leap.intent.derived").roots().get(0).contents()
                .get("constraints");
        assertThat(byClass(constraints, "SpanConstraint").attributes()).containsEntry("hand", "RIGHT")
                .containsEntry("maxSimultaneousSemitones", 9).containsEntry("maxStretchInPosition", 9);
        assertThat(constraints).filteredOn(c -> c.eClass().equals("ContainsPattern")).hasSize(2);
        mvc.perform(post("/api/models/giulia-leap.intent.weaving/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /** A copy of a node with one more child under a containment. */
    private static ObjectNode plus(ObjectNode node, String containment, ObjectNode child) {
        Map<String, List<ObjectNode>> contents = new java.util.LinkedHashMap<>(node.contents());
        List<ObjectNode> children = new ArrayList<>(contents.getOrDefault(containment, List.of()));
        children.add(child);
        contents.put(containment, children);
        return new ObjectNode(node.id(), node.eClass(), node.attributes(), contents, node.references());
    }

    @Test
    void aNeedTypeAddedOnlyToTheCatalogChangesTheSpec() throws Exception {
        // The type level is a model: a kind of need the code has never heard of, with a parameter,
        // a constraint that reaches a contained element and a ceiling on an attribute of the spec.
        ObjectNode maxBars = new ObjectNode("Tiring_maxBars", "ParameterDef",
                Map.of("name", "maxBars", "label", "Battute", "kind", "INTEGER", "min", 1.0), null, null);
        ObjectNode noEighths = new ObjectNode("Tiring_plain", "ConstraintEffect",
                Map.of("constraintClass", "ExcludesPattern", "rationale", "Si stanca presto: niente crome"),
                Map.of("bindings", List.of(
                        new ObjectNode("Tiring_plain_kind", "Binding", Map.of("feature", "pattern.kind", "literal", "RHYTHM"), null, null),
                        new ObjectNode("Tiring_plain_expression", "Binding", Map.of("feature", "pattern.expression", "literal", "8 8"), null, null))),
                null);
        ObjectNode shorter = new ObjectNode("Tiring_short", "SpecEffect",
                Map.of("feature", "bars", "combine", "MIN", "rationale", "Si stanca presto: al massimo {maxBars} battute"),
                null, Map.of("parameter", List.of(new Ref(null, "Tiring_maxBars", null))));
        ObjectNode tiring = new ObjectNode("Tiring", "NeedType", Map.of("name", "Tiring", "label", "Si stanca presto"),
                Map.of("parameters", List.of(maxBars), "effects", List.of(noEighths, shorter)), null);
        ModelDocument catalog = store.get(WS, "default.needs");
        store.replace(WS, "default.needs", catalog.withRoots(List.of(plus(catalog.roots().get(0), "types", tiring))));
        mvc.perform(post("/api/models/default.needs/validate")).andExpect(jsonPath("$", hasSize(0)));

        ObjectNode slot = new ObjectNode("need_tiring_maxBars", "Slot", Map.of("value", "6"), null,
                Map.of("parameter", List.of(new Ref("default.needs", "Tiring_maxBars", null))));
        ObjectNode need = new ObjectNode("need_tiring", "Need", Map.of(), Map.of("slots", List.of(slot)),
                Map.of("type", List.of(new Ref("default.needs", "Tiring", null))));
        ModelDocument profile = store.get(WS, "giulia.profile");
        store.replace(WS, "giulia.profile", profile.withRoots(List.of(plus(profile.roots().get(0), "needs", need))));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());

        ObjectNode spec = store.get(WS, "giulia.intent.derived").roots().get(0);
        assertThat(spec.attributes()).containsEntry("bars", 6);
        List<ObjectNode> constraints = spec.contents().get("constraints");
        assertThat(constraints).extracting(ObjectNode::id).containsExactly("c_contains_1", "c_excludes_1", "c_range_1",
                "c_render_1", "c_span_1", "c_excludes_2");
        ObjectNode excludes = constraints.get(5);
        assertThat(excludes.attributes()).containsEntry("strength", "HARD");
        ObjectNode pattern = excludes.contents().get("pattern").get(0);
        assertThat(pattern.id()).isEqualTo("c_excludes_2_pattern");
        assertThat(pattern.attributes()).containsEntry("kind", "RHYTHM").containsEntry("expression", "8 8");

        List<ObjectNode> links = store.get(WS, "giulia.intent.weaving").roots().get(0).contents().get("links");
        ObjectNode ceiling = links.stream().filter(l -> "ApplyNeedLimits".equals(l.attributes().get("rule")))
                .findFirst().orElseThrow();
        assertThat(ceiling.eClass()).isEqualTo("ConflictFix");
        assertThat(ceiling.attributes()).containsEntry("targetFeature", "bars").containsEntry("originalValue", "8")
                .containsEntry("newValue", "6").containsEntry("critique", "NeedLimitExceeded")
                .containsEntry("rationale", "Si stanca presto: al massimo 6 battute");
        assertThat(ceiling.contents().get("sources")).extracting(source -> source.attributes().get("origin") + " "
                + source.references().get("element").get(0).id()).containsExactly("PROFILE need_tiring_maxBars",
                        "NEED_CATALOG Tiring_short");
        // Every element the need added, the contained pattern too, is traced.
        mvc.perform(post("/api/models/giulia.intent.weaving/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aDimensionAddedOnlyToTheQualityModelBecomesAGoalOfTheSpec() throws Exception {
        // A dimension the code has never heard of: implicit at MEDIUM, measured with an existing metric.
        ObjectNode band = new ObjectNode("w_med", "Band", Map.of("level", "MEDIUM", "min", 0.0, "max", 1.0, "hint", "Use about an octave."), null, null);
        ObjectNode range = new ObjectNode("RangeUse", "QualityDimension",
                Map.of("name", "RangeUse", "label", "Estensione usata", "metric", "rangeUse", "implicit", true, "defaultLevel", "MEDIUM"),
                Map.of("bands", List.of(band)), null);
        ModelDocument quality = store.get(WS, "piano.quality");
        store.replace(WS, "piano.quality", quality.withRoots(List.of(plus(quality.roots().get(0), "dimensions", range))));
        mvc.perform(post("/api/models/piano.quality/validate")).andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());

        List<ObjectNode> goals = store.get(WS, "giulia.intent.derived").roots().get(0).contents().get("qualityGoals");
        assertThat(goals).extracting(ObjectNode::id).containsExactly("qg_rhythmic_complexity", "qg_melodic_complexity",
                "qg_range_use");
        assertThat(goals.get(2).attributes()).containsEntry("level", "MEDIUM");
        List<ObjectNode> links = store.get(WS, "giulia.intent.weaving").roots().get(0).contents().get("links");
        List<ObjectNode> about = links.stream().filter(l -> l.references().get("target").get(0).id().equals("qg_range_use")).toList();
        assertThat(about).extracting(l -> l.attributes().get("rule")).containsExactly("ApplyImplicitGoals", "ResolveThresholds");
        assertThat(about.get(0).attributes()).containsEntry("rationale",
                "La docente non indica un obiettivo per Estensione usata: vale il livello MEDIUM previsto dal modello di qualità");
        assertThat(about.get(0).contents().get("sources").get(0).attributes()).containsEntry("origin", "QUALITY_MODEL")
                .containsEntry("feature", "defaultLevel");
        assertThat(about.get(1).attributes()).containsEntry("rationale", "Estensione usata MEDIUM significa rangeUse tra 0.0 e 1.0");
    }

    @Test
    void anIntentWithoutAQualityModelDerivesWithoutGoals() throws Exception {
        ModelDocument intent = store.get(WS, "giulia.intent");
        ObjectNode root = intent.roots().get(0);
        Map<String, List<ObjectNode>> contents = new java.util.LinkedHashMap<>(root.contents());
        contents.remove("qualityGoals");
        Map<String, List<Ref>> references = new java.util.LinkedHashMap<>(root.references());
        references.remove("qualityModel");
        store.replace(WS, "giulia.intent", intent.withRoots(List.of(
                new ObjectNode(root.id(), root.eClass(), root.attributes(), contents, references))));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        ObjectNode spec = store.get(WS, "giulia.intent.derived").roots().get(0);
        assertThat(spec.contents().get("qualityGoals")).isNullOrEmpty();
        assertThat(spec.references().get("qualityModel")).isNullOrEmpty();
        mvc.perform(post("/api/models/giulia.intent.weaving/validate")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void whatIsDerivedGoesInTheFolderOfTheIntentAndThenStaysWhereItIsPut() throws Exception {
        store.move(WS, "giulia.intent", "Classe 3/Giulia");
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.meta(WS, "giulia.intent.derived").folder()).isEqualTo("Classe 3/Giulia");
        assertThat(store.meta(WS, "giulia.intent.weaving").folder()).isEqualTo("Classe 3/Giulia");

        store.move(WS, "giulia.intent.weaving", "Tracce");
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.meta(WS, "giulia.intent.weaving").folder()).isEqualTo("Tracce");
        assertThat(store.meta(WS, "giulia.intent.weaving").version()).isEqualTo(2);
    }

    private void addNeed(String id, String type, String parameter, String value) {
        ObjectNode slot = new ObjectNode(id + "_" + parameter, "Slot", Map.of("value", value), null,
                Map.of("parameter", List.of(new Ref("default.needs", type + "_" + parameter, null))));
        ObjectNode need = new ObjectNode(id, "Need", Map.of(), Map.of("slots", List.of(slot)),
                Map.of("type", List.of(new Ref("default.needs", type, null))));
        ModelDocument profile = store.get(WS, "giulia.profile");
        store.replace(WS, "giulia.profile", profile.withRoots(List.of(plus(profile.roots().get(0), "needs", need))));
    }

    private ObjectNode needLimitLink() {
        List<ObjectNode> links = store.get(WS, "giulia.intent.weaving").roots().get(0).contents().get("links");
        return links.stream().filter(l -> "ApplyNeedLimits".equals(l.attributes().get("rule"))).findFirst().orElseThrow();
    }

    @Test
    void aNeedThatOverridesTheTeacherIsWarnedOnTheIntentAndTracedAsItsFix() throws Exception {
        // Giulia is asked 90 bpm and is comfortable at 72: the need lowers the tempo further.
        addNeed("need_slow", "ReducedMotorSpeed", "maxBpm", "60");
        mvc.perform(post("/api/models/giulia.intent/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraint == 'NeedLimitExceeded')].severity", contains("WARNING")))
                .andExpect(jsonPath("$[?(@.constraint == 'NeedLimitExceeded')].message", contains(
                        "Velocità motoria ridotta: il tempo non supera 60 bpm (richiesto: 90): "
                                + "la derivazione applicherà il limite di giulia")));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.get(WS, "giulia.intent.derived").roots().get(0).attributes()).containsEntry("maxBpm", 60);
        ObjectNode link = needLimitLink();
        assertThat(link.eClass()).isEqualTo("ConflictFix");
        assertThat(link.attributes()).containsEntry("critique", "NeedLimitExceeded")
                .containsEntry("originalValue", "72").containsEntry("newValue", "60");
    }

    @Test
    void aNeedThatOnlyTightensAValueOfTheProfileIsADerivation() throws Exception {
        // 66 bpm is above nothing the teacher asked once the request is within it, but below the comfortable 72.
        ModelDocument intent = store.get(WS, "giulia.intent");
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(intent.roots().get(0).attributes());
        attributes.remove("requestedBpm");
        ObjectNode root = intent.roots().get(0);
        store.replace(WS, "giulia.intent", intent.withRoots(List.of(
                new ObjectNode(root.id(), root.eClass(), attributes, root.contents(), root.references()))));
        addNeed("need_slow", "ReducedMotorSpeed", "maxBpm", "66");

        mvc.perform(post("/api/models/giulia.intent/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraint == 'NeedLimitExceeded')]", hasSize(0)));
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.get(WS, "giulia.intent.derived").roots().get(0).attributes()).containsEntry("maxBpm", 66);
        assertThat(needLimitLink().eClass()).isEqualTo("Derivation");
    }

    @Test
    void aCeilingThatIsAlreadyRespectedLeavesTheSpecAsItIsAndAProfileWithoutNeedsStillDerives() throws Exception {
        ObjectNode slot = new ObjectNode("need_attention_maxBars", "Slot", Map.of("value", "16"), null,
                Map.of("parameter", List.of(new Ref("default.needs", "ShortAttentionSpan_maxBars", null))));
        ObjectNode need = new ObjectNode("need_attention", "Need", Map.of(), Map.of("slots", List.of(slot)),
                Map.of("type", List.of(new Ref("default.needs", "ShortAttentionSpan", null))));
        ModelDocument profile = store.get(WS, "giulia.profile");
        store.replace(WS, "giulia.profile", profile.withRoots(List.of(plus(profile.roots().get(0), "needs", need))));
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.get(WS, "giulia.intent.derived").roots().get(0).attributes()).containsEntry("bars", 8);
        assertThat(store.get(WS, "giulia.intent.weaving").roots().get(0).contents().get("links"))
                .noneMatch(l -> "ApplyNeedLimits".equals(l.attributes().get("rule")));

        // No needs and no catalog: the script sees an empty catalog model and goes on.
        ObjectNode learner = store.get(WS, "giulia.profile").roots().get(0);
        Map<String, List<ObjectNode>> contents = new java.util.LinkedHashMap<>(learner.contents());
        contents.remove("needs");
        ModelDocument current = store.get(WS, "giulia.profile");
        store.replace(WS, "giulia.profile", current.withRoots(List.of(
                new ObjectNode(learner.id(), learner.eClass(), learner.attributes(), contents, Map.of()))));
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        ObjectNode spec = store.get(WS, "giulia.intent.derived").roots().get(0);
        assertThat(spec.contents().get("constraints")).extracting(ObjectNode::id).containsExactly("c_contains_1",
                "c_excludes_1", "c_range_1");
        assertThat(store.get(WS, "giulia.intent.weaving").roots().get(0).references()).doesNotContainKey("needCatalog");
    }

    @Test
    void derivingTwiceOverwritesTheOutputsWithTheSameIds() throws Exception {
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk())
                // Without the fix the tempo warning of the intent is still reported.
                .andExpect(jsonPath("$.diagnostics", hasSize(1)))
                .andExpect(jsonPath("$.diagnostics[0].constraint").value("TempoAboveComfort"));
        String first = json.writeValueAsString(store.get(WS, "giulia.intent.weaving").roots());
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        assertThat(store.get(WS, "giulia.intent.weaving").version()).isEqualTo(2);
        assertThat(store.get(WS, "giulia.intent.derived").version()).isEqualTo(2);
        assertThat(json.writeValueAsString(store.get(WS, "giulia.intent.weaving").roots())).isEqualTo(first);
        // The clamp is applied and traced even when the fix was not accepted.
        assertThat(store.get(WS, "giulia.intent.derived").roots().get(0).attributes()).containsEntry("maxBpm", 72);
    }

    @Test
    void noConflictFixWhenTheRequestedTempoIsComfortable() throws Exception {
        ModelDocument intent = store.get(WS, "giulia.intent");
        ObjectNode root = intent.roots().get(0);
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(root.attributes());
        attributes.put("requestedBpm", 66);
        store.replace(WS, "giulia.intent", intent.withRoots(
                List.of(new ObjectNode(root.id(), root.eClass(), attributes, root.contents(), root.references()))));

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.diagnostics", hasSize(0)));
        assertThat(store.get(WS, "giulia.intent.derived").roots().get(0).attributes()).containsEntry("maxBpm", 66);
        List<ObjectNode> links = store.get(WS, "giulia.intent.weaving").roots().get(0).contents().get("links");
        assertThat(links).noneMatch(l -> l.eClass().equals("ConflictFix"));
        assertThat(links).anyMatch(l -> l.eClass().equals("Refinement")
                && "TempoLimit".equals(l.attributes().get("rule")) && "maxBpm".equals(l.attributes().get("targetFeature")));
    }

    @Test
    void refusesInputsWithErrorsAndBadRequests() throws Exception {
        // A learner without name is a structural error of the profile.
        mvc.perform(put("/api/models/giulia.profile").contentType(MediaType.APPLICATION_JSON).content("""
                {"version":1,"roots":[{"id":"giulia","eClass":"Learner","attributes":{"comfortableBpm":72}}]}"""))
                .andExpect(status().isOk());
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("giulia.profile")));
        mvc.perform(get("/api/models/giulia.intent.derived")).andExpect(status().isNotFound());

        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"learnerModelId\":\"giulia.profile\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content("""
                {"learnerModelId":"piano.quality","teacherIntentModelId":"giulia.intent","qualityModelId":"piano.quality"}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content("""
                {"learnerModelId":"nope","teacherIntentModelId":"giulia.intent","qualityModelId":"piano.quality"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    void theTransformationScriptIsDescribedCheckedEditedAndRestored() throws Exception {
        mvc.perform(get("/api/derivations/script")).andExpect(status().isOk())
                .andExpect(jsonPath("$.script").value("derive.etl")).andExpect(jsonPath("$.modified").value(false))
                .andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.inputs[*].alias", contains("L", "T", "Q", "N")))
                .andExpect(jsonPath("$.outputs[*].alias", contains("S", "W")))
                .andExpect(jsonPath("$.rules[0].name").value("CopyIntent"))
                .andExpect(jsonPath("$.rules[0].from").value("T!ExerciseSpec"))
                .andExpect(jsonPath("$.rules[0].to", contains("S!ExerciseSpec")))
                .andExpect(jsonPath("$.rules[0].guard").value("t = intent()"))
                .andExpect(jsonPath("$.rules[0].comment", containsString("The spec starts as a copy")))
                .andExpect(jsonPath("$.rules[*].name", hasItem("RangeFromLearner")))
                .andExpect(jsonPath("$.blocks[*].kind", hasItems("pre", "post")))
                .andExpect(jsonPath("$.blocks[*].name", hasItems("TempoLimit", "ApplyNeeds", "ApplyNeedLimits")));

        String original = Scripts.read("derive.etl");
        java.util.function.Function<String, String> body = source -> {
            try {
                return json.writeValueAsString(java.util.Map.of("source", source));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };

        // a syntax error is reported with its position and nothing is saved
        String broken = original.replace("rule CopyIntent\n  transform t", "rule CopyIntent\n  transfrom t");
        mvc.perform(post("/api/derivations/script/check").contentType(MediaType.APPLICATION_JSON)
                .content(body.apply(broken))).andExpect(status().isOk())
                .andExpect(jsonPath("$.problems[0].line").value(36)).andExpect(jsonPath("$.rules", hasSize(0)));
        mvc.perform(put("/api/derivations/script").contentType(MediaType.APPLICATION_JSON).content(body.apply(broken)))
                .andExpect(status().isUnprocessableEntity());

        // right syntax, wrong property: found by deriving on trial from the workspace models
        String wrong = original.replace("  s.key = t.key;", "  s.key = t.noSuchThing;");
        mvc.perform(post("/api/derivations/script/check").contentType(MediaType.APPLICATION_JSON)
                .content(body.apply(wrong))).andExpect(status().isOk()).andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.failures[0].modelId").value("giulia.intent"))
                .andExpect(jsonPath("$.failures[0].message", containsString("Property 'noSuchThing' not found")));
        mvc.perform(put("/api/derivations/script").contentType(MediaType.APPLICATION_JSON).content(body.apply(wrong)))
                .andExpect(status().isUnprocessableEntity());
        // the trial leaves nothing behind
        assertThat(store.exists(WS, "giulia.intent.derived")).isFalse();

        // a sound change is kept and changes what the derivation produces
        String changed = original.replace("operation LEARNING_MIN_OCCURRENCES() : Integer { return 3; }",
                "operation LEARNING_MIN_OCCURRENCES() : Integer { return 5; }");
        mvc.perform(put("/api/derivations/script").contentType(MediaType.APPLICATION_JSON).content(body.apply(changed)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modified").value(true));
        mvc.perform(post("/api/models/giulia.intent/fixes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosticId\":\"giulia.intent#TempoAboveComfort@intent\",\"fixIndex\":0}"));
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        mvc.perform(get("/api/models/giulia.intent.derived")).andExpect(jsonPath(
                "$.roots[0].contents.constraints[?(@.eClass=='ContainsPattern')].attributes.minOccurrences", contains(5)));

        mvc.perform(delete("/api/derivations/script")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modified").value(false));
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content(DERIVE))
                .andExpect(status().isOk());
        mvc.perform(get("/api/models/giulia.intent.derived")).andExpect(jsonPath(
                "$.roots[0].contents.constraints[?(@.eClass=='ContainsPattern')].attributes.minOccurrences", contains(3)));
    }
}
