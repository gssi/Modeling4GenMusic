package org.modeling4genmusic.studio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The generation stub on the spec derived for Giulia. */
@SpringBootTest
@AutoConfigureMockMvc
class GenerationApiTest {

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
    BasicExerciseValidator validator;

    @BeforeAll
    static void deriveGiulia(@Autowired MockMvc mvc) throws Exception {
        for (String file : new String[] { "default.needs.xmi", "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : "")).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content("""
                {"learnerModelId":"giulia.profile","teacherIntentModelId":"giulia.intent","qualityModelId":"piano.quality"}"""))
                .andExpect(status().isOk());
    }

    private EObject derivedSpec() {
        return store.load("default", "giulia.intent.derived").getContents().get(0);
    }

    @Test
    void theMockExerciseIsValidForTheDerivedSpec() throws Exception {
        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specModelId\":\"giulia.intent.derived\",\"qualityModelId\":\"piano.quality\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.abc", containsString("K:G")))
                .andExpect(jsonPath("$.abc", containsString("G2 B2 d2 B2 | c>B A2 G2 |")))
                .andExpect(jsonPath("$.metadata.generator").value("mock"))
                .andExpect(jsonPath("$.metadata.bars").value(8))
                .andExpect(jsonPath("$.metadata.tempo").value("1/4=72"))
                .andExpect(jsonPath("$.report.valid").value(true))
                .andExpect(jsonPath("$.report.checks", hasSize(7)))
                .andExpect(jsonPath("$.report.checks[*].name", contains("bars", "barLength", "containsPattern",
                        "excludesPattern", "range", "handSpan", "positionChanges")))
                .andExpect(jsonPath("$.report.checks[5].passed").value(true))
                .andExpect(jsonPath("$.report.checks[5].blocking").value(true))
                .andExpect(jsonPath("$.report.checks[5].message", containsString("Non ci sono note simultanee")))
                .andExpect(jsonPath("$.report.checks[6].blocking").value(false))
                .andExpect(jsonPath("$.report.checks[6].message", containsString("Nessun cambio di posizione")))
                .andExpect(jsonPath("$.report.checks[?(@.blocking == true)].passed", everyItem(is(true))))
                // The sample tune has four short bars: reported, without making the exercise invalid.
                .andExpect(jsonPath("$.report.checks[1].passed").value(false))
                .andExpect(jsonPath("$.report.checks[1].blocking").value(false))
                .andExpect(jsonPath("$.report.checks[1].message").value("Le battute 2, 4, 6, 7 non durano 4/4"))
                .andExpect(jsonPath("$.report.checks[2].message", containsString("4 volte")));
    }

    @Test
    void theQualityModelIsOptionalAndBadRequestsAreRefused() throws Exception {
        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specModelId\":\"giulia.intent.derived\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.report.valid").value(true));
        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specModelId\":\"giulia.profile\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specModelId\":\"nope\"}")).andExpect(status().isNotFound());
    }

    private Map<String, Check> checks(String abc) {
        ValidationReport report = validator.validate(abc, derivedSpec(), null);
        assertThat(report.valid()).isFalse();
        return report.checks().stream().collect(java.util.stream.Collectors.toMap(Check::name, c -> c));
    }

    @Test
    void reportsEachViolatedConstraint() {
        String header = "X:1\nM:4/4\nL:1/8\nK:G\n";
        // Two bars instead of eight, one dotted figure instead of three, the excluded figure, a note too low.
        Map<String, Check> checks = checks(header + "G2 B2 c>B A2 | c<B B,2 G4 |]");
        assertThat(checks.get("bars").passed()).isFalse();
        assertThat(checks.get("bars").message()).contains("2 invece di 8");
        assertThat(checks.get("containsPattern").passed()).isFalse();
        assertThat(checks.get("containsPattern").message()).contains("1 volta");
        assertThat(checks.get("excludesPattern").passed()).isFalse();
        assertThat(checks.get("range").passed()).isFalse();
        assertThat(checks.get("range").message()).contains("1 nota").contains("60–79");

        assertThat(checks("not abc at all").get("abc").message()).contains("non è leggibile");
    }

    @Test
    void aChordWiderThanTheHandOpensIsABlockingViolationAndLeapsAreOnlyCounted() {
        String header = "X:1\nM:4/4\nL:1/8\nK:G\n";
        // The octave dyad [Gg] cannot be played by a hand that opens a major sixth; G to g is a change of position.
        Map<String, Check> checks = checks(header + "[Gg]2 B2 c>B A2 | G2 g2 d2 B2 |]");
        assertThat(checks.get("handSpan").passed()).isFalse();
        assertThat(checks.get("handSpan").blocking()).isTrue();
        assertThat(checks.get("handSpan").message()).contains("12 semitoni (ottava)").contains("mano destra")
                .contains("9 semitoni, sesta maggiore");
        assertThat(checks.get("positionChanges").passed()).isTrue();
        assertThat(checks.get("positionChanges").blocking()).isFalse();
        assertThat(checks.get("positionChanges").message()).startsWith("1 cambio di posizione");

        // A sixth fits: only the other constraints of this short tune fail.
        Map<String, Check> sixth = checks(header + "[GB]2 [Ge]2 c>B A2 |]");
        assertThat(sixth.get("handSpan").passed()).isTrue();
        assertThat(sixth.get("handSpan").message()).contains("9 semitoni (sesta maggiore), entro");
    }
}
