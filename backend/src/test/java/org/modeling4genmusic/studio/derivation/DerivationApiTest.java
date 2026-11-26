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
                "giulia.profile", "piano.quality" }) {
            if (store.exists(WS, id)) {
                store.delete(WS, id);
            }
        }
        for (String file : new String[] { "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
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
        assertThat(goals.get(0).attributes()).containsEntry("attribute", "RHYTHMIC_COMPLEXITY")
                .containsEntry("level", "MEDIUM");
        assertThat(goals.get(1).attributes()).containsEntry("attribute", "MELODIC_COMPLEXITY")
                .containsEntry("level", "LOW");

        // ---- weaving
        ObjectNode weaving = store.get(WS, "giulia.intent.weaving").roots().get(0);
        assertThat(weaving.references()).containsEntry("learner", List.of(new Ref("giulia.profile", "giulia", null)))
                .containsEntry("teacherIntent", List.of(new Ref("giulia.intent", "intent", null)))
                .containsEntry("qualityModel", List.of(new Ref("piano.quality", "piano_quality", null)))
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
                "RangeFromLearner", "RenderForLowVision", "SpanFromHandLimit", "CopyQualityGoals",
                "FocusOtherDimensionsLow", "ResolveThresholds");
        ObjectNode span = links.stream().filter(l -> "SpanFromHandLimit".equals(l.attributes().get("rule")))
                .findFirst().orElseThrow();
        assertThat(span.eClass()).isEqualTo("Derivation");
        assertThat(span.attributes()).containsEntry("rationale",
                "Limite di apertura della mano destra: massimo 9 semitoni (sesta maggiore)");
        assertThat(span.references().get("target").get(0).id()).isEqualTo("c_span_1");
        ObjectNode spanSource = span.contents().get("sources").get(0);
        assertThat(spanSource.attributes()).containsEntry("origin", "PROFILE").containsEntry("feature",
                "maxStretchSemitones");
        assertThat(spanSource.references().get("element"))
                .containsExactly(new Ref("giulia.profile", "need_handspan", null));

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
                .andExpect(jsonPath("$.inputs[*].alias", contains("L", "T", "Q")))
                .andExpect(jsonPath("$.outputs[*].alias", contains("S", "W")))
                .andExpect(jsonPath("$.rules[0].name").value("CopyIntent"))
                .andExpect(jsonPath("$.rules[0].from").value("T!ExerciseSpec"))
                .andExpect(jsonPath("$.rules[0].to", contains("S!ExerciseSpec")))
                .andExpect(jsonPath("$.rules[0].guard").value("t = intent()"))
                .andExpect(jsonPath("$.rules[0].comment", containsString("The spec starts as a copy")))
                .andExpect(jsonPath("$.rules[*].name", hasItem("SpanFromHandLimit")))
                .andExpect(jsonPath("$.blocks[*].kind", hasItems("pre", "post")))
                .andExpect(jsonPath("$.blocks[*].name", hasItem("TempoLimit")));

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
                .andExpect(jsonPath("$.problems[0].line").value(39)).andExpect(jsonPath("$.rules", hasSize(0)));
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
