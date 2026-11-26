package org.modeling4genmusic.studio.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.rhythm.RhythmPattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** The Ollama generator against a stand-in server: no model is needed to run these tests. */
@SpringBootTest
@AutoConfigureMockMvc
class OllamaGenerationTest {

    private static final String MODEL = "test-music-model";
    /** Eight bars in G, inside C4–G5, with four dotted pairs and no reversed one. */
    private static final String GOOD = "G2 B2 d2 B2 | c>B A2 G2 B2 | A2 B2 c2 A2 | B>A G2 D2 E2 |\n"
            + "E2 G2 B2 d2 | e>d c2 B2 A2 | A2 B>A G2 F2 | G8 |]";
    private static final String TOO_HIGH = GOOD.replace("G8", "c'8");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Deque<String> answers = new ArrayDeque<>();
    private static final List<String> prompts = new ArrayList<>();
    private static volatile List<String> installedModels = List.of(MODEL);
    private static HttpServer ollama;

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ollama.createContext("/api/tags", exchange -> reply(exchange, 200, Map.of("models",
                installedModels.stream().map(name -> Map.of("name", name, "model", name)).toList())));
        ollama.createContext("/api/generate", exchange -> {
            prompts.add(JSON.readTree(exchange.getRequestBody()).path("prompt").asText());
            reply(exchange, 200, Map.of("response", answers.isEmpty() ? "" : answers.poll()));
        });
        ollama.start();
        registry.add("studio.workspace-dir", () -> workspace.toString());
        registry.add("studio.ollama.base-url", () -> "http://localhost:" + ollama.getAddress().getPort());
        registry.add("studio.ollama.model", () -> MODEL);
        registry.add("studio.ollama.feedback", () -> "true");
    }

    private static void reply(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @AfterAll
    static void stopOllama() {
        ollama.stop(0);
    }

    @Autowired
    MockMvc mvc;

    @BeforeAll
    static void deriveGiulia(@Autowired MockMvc mvc) throws Exception {
        for (String file : new String[] { "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : "")).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/derivations").contentType(MediaType.APPLICATION_JSON).content("""
                {"learnerModelId":"giulia.profile","teacherIntentModelId":"giulia.intent","qualityModelId":"piano.quality"}"""))
                .andExpect(status().isOk());
    }

    @BeforeEach
    void reset() {
        answers.clear();
        prompts.clear();
        installedModels = List.of(MODEL);
    }

    private ResultActions generate(String generator) throws Exception {
        return mvc.perform(post("/api/generations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"specModelId\":\"giulia.intent.derived\",\"generator\":\"" + generator + "\"}"));
    }

    @Test
    void asksTheModelWithTheSpecAndReturnsAValidExercise() throws Exception {
        answers.add(GOOD);
        generate("ollama").andExpect(status().isOk()).andExpect(jsonPath("$.report.valid").value(true))
                .andExpect(jsonPath("$.metadata.generator").value("ollama"))
                .andExpect(jsonPath("$.metadata.model").value(MODEL))
                .andExpect(jsonPath("$.metadata.attempts").value(1)).andExpect(jsonPath("$.attempts", hasSize(1)))
                .andExpect(jsonPath("$.abc", containsString("M:4/4\nK:G\nG2 B2 d2 B2 |")))
                .andExpect(jsonPath("$.abc", containsString("Q:1/4=72")));

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0)).startsWith("Human: " + OllamaExerciseGenerator.TASK).contains("Meter 4/4")
                .contains("Key G").contains("Tempo 1/4=72").contains("A 8 bar exercise for a beginner piano player.")
                .contains("All notes stay between C4 and G5.")
                .contains("It practises the rhythm c>B, used at least 3 times.")
                .contains("It never uses the rhythm c<B.")
                // the header is written for the model, which only continues with the notes
                .endsWith(" </s> Assistant: X:1\nT:Esercizio\nL:1/8\nQ:1/4=72\nM:4/4\nK:G\n");
    }

    @Test
    void asksAgainTellingWhatWasWrongAndKeepsTheFirstValidExercise() throws Exception {
        answers.add(TOO_HIGH);
        answers.add(GOOD);
        generate("ollama").andExpect(status().isOk()).andExpect(jsonPath("$.report.valid").value(true))
                .andExpect(jsonPath("$.metadata.attempts").value(2)).andExpect(jsonPath("$.attempts", hasSize(2)))
                .andExpect(jsonPath("$.attempts[0].report.valid").value(false))
                .andExpect(jsonPath("$.attempts[0].abc", containsString("c'8")))
                .andExpect(jsonPath("$.abc", not(containsString("c'8"))));
        assertThat(prompts).hasSize(2);
        assertThat(prompts.get(0)).doesNotContain("Keep every note inside that range");
        assertThat(prompts.get(1)).contains("Keep every note inside that range");
    }

    @Test
    void givesBackTheBestRejectedExerciseAfterThreeAttempts() throws Exception {
        answers.add("c'8 | c'8 | c'8 | c'8 | c'8 | c'8 | c'8 | c'8 |]"); // out of range and without the pattern
        answers.add(TOO_HIGH); // only out of range
        answers.add("this is not music");
        generate("ollama").andExpect(status().isOk()).andExpect(jsonPath("$.report.valid").value(false))
                .andExpect(jsonPath("$.metadata.attempts").value(3)).andExpect(jsonPath("$.attempts", hasSize(3)))
                .andExpect(jsonPath("$.abc", containsString("c>B A2 G2")));
        assertThat(prompts.get(1)).contains("Use that rhythm more often.");
    }

    @Test
    void tidiesWhatTheModelWritesAndStopsAtTheRequestedBars() throws Exception {
        answers.add("\"^Moderato\"!mf! D2 |:\"G\" G2 B2 {c}d2 B2 |\"C\" c>B A2 G2 B2 | A2 B2 c2 A2 |1 B>A G2 D2 E2 :|2\n"
                + "E2 G2 B2 d2 || e>d c2 B2 A2 | A2 B>A G2 F2 | G8 | G8 | A8 | B8 |]\n\nX:2\nK:C\nC8 |]");
        generate("ollama").andExpect(status().isOk()).andExpect(jsonPath("$.report.valid").value(true))
                .andExpect(jsonPath("$.abc", not(containsString("\"")))).andExpect(jsonPath("$.abc", not(containsString("!"))))
                .andExpect(jsonPath("$.abc", not(containsString(":|"))))
                // the pickup bar is dropped and the tune stops after eight bars
                .andExpect(jsonPath("$.abc", containsString("K:G\nG2 B2 d2 B2 | c>B A2 G2 B2 |")))
                .andExpect(jsonPath("$.abc", containsString("| A2 B>A G2 F2 | G8 |]")))
                .andExpect(jsonPath("$.abc", not(containsString("G8 | G8"))))
                .andExpect(jsonPath("$.report.checks[0].message").value("Le battute sono 8, come richiesto"));
    }

    @Test
    void reportsWhenTheModelOrTheServerIsMissing() throws Exception {
        mvc.perform(get("/api/generations/generators")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='mock')].available").value(true))
                .andExpect(jsonPath("$[?(@.id=='mock')].isDefault").value(true))
                .andExpect(jsonPath("$[?(@.id=='ollama')].available").value(true))
                .andExpect(jsonPath("$[?(@.id=='ollama')].model").value(MODEL));

        installedModels = List.of("another-model");
        mvc.perform(get("/api/generations/generators"))
                .andExpect(jsonPath("$[?(@.id=='ollama')].available").value(false))
                .andExpect(jsonPath("$[?(@.id=='ollama')].reason").value(hasItem(containsString("ollama pull " + MODEL))));
        generate("ollama").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("ollama pull")));
        assertThat(prompts).isEmpty();

        generate("nope").andExpect(status().isBadRequest());
        generate("mock").andExpect(status().isOk()).andExpect(jsonPath("$.metadata.attempts").value(1));
    }

    @Test
    void writesRhythmsAndNoteNamesForTheRequest() {
        assertThat(OllamaExerciseGenerator.abcOf(RhythmPattern.parse("8. 16"))).isEqualTo("c>B");
        assertThat(OllamaExerciseGenerator.abcOf(RhythmPattern.parse("16 8."))).isEqualTo("c<B");
        assertThat(OllamaExerciseGenerator.abcOf(RhythmPattern.parse("4 8 8"))).isEqualTo("c2BA");
        assertThat(OllamaExerciseGenerator.abcOf(RhythmPattern.parse("16 16 2"))).isEqualTo("c/2B/2A4");
        assertThat(OllamaExerciseGenerator.noteName(60)).isEqualTo("C4");
        assertThat(OllamaExerciseGenerator.noteName(79)).isEqualTo("G5");
        assertThat(AbcExtractor.bars("|: A2 B2 :|[2 c4 || z4 |]")).containsExactly("A2 B2", "c4", "z4");
    }
}
