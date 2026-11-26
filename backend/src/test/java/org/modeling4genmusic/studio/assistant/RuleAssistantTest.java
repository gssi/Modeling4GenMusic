package org.modeling4genmusic.studio.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** The rule assistant against a stand-in server: no language model is needed to run these tests. */
@SpringBootTest
@AutoConfigureMockMvc
class RuleAssistantTest {

    private static final String MODEL = "test-code-model";
    private static final String TOO_MANY_BARS = """
            context T!ExerciseSpec {

              critique TooManyBars {
                check: self.bars <= 4
                message: "L'esercizio ha più di 4 battute"
              }
            }""";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Deque<String> answers = new ArrayDeque<>();
    private static final List<JsonNode> requests = new ArrayList<>();
    private static volatile List<String> installedModels = List.of(MODEL);
    private static HttpServer ollama;

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        ollama = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ollama.createContext("/api/tags", exchange -> reply(exchange, Map.of("models",
                installedModels.stream().map(name -> Map.of("name", name, "model", name)).toList())));
        ollama.createContext("/api/chat", exchange -> {
            requests.add(JSON.readTree(exchange.getRequestBody()));
            reply(exchange, Map.of("message", Map.of("role", "assistant", "content",
                    answers.isEmpty() ? "" : answers.poll())));
        });
        ollama.start();
        registry.add("studio.workspace-dir", () -> workspace.toString());
        registry.add("studio.ollama.base-url", () -> "http://localhost:" + ollama.getAddress().getPort());
        registry.add("studio.assistant.model", () -> MODEL);
    }

    private static void reply(HttpExchange exchange, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(body);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
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
    static void importGiulia(@Autowired MockMvc mvc) throws Exception {
        for (String file : new String[] { "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : "")).andExpect(status().isCreated());
        }
    }

    @BeforeEach
    void reset() {
        answers.clear();
        requests.clear();
        installedModels = List.of(MODEL);
    }

    private ResultActions propose(String script, String description) throws Exception {
        return mvc.perform(post("/api/assistant/rules").contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("script", script, "source", Scripts.read(script),
                        "description", description))));
    }

    private static String message(int request, int index) {
        return requests.get(request).path("messages").get(index).path("content").asText();
    }

    @Test
    void asksWithWhatTheScriptSeesAndReturnsACheckedRuleWithItsEffects() throws Exception {
        answers.add("Ecco la regola:\n```evl\n" + TOO_MANY_BARS + "\n```\nSpero sia utile.");
        propose("intent.evl", "Avvisa se le battute sono più di 4").andExpect(status().isOk())
                .andExpect(jsonPath("$.rule").value(TOO_MANY_BARS)).andExpect(jsonPath("$.attempts").value(1))
                .andExpect(jsonPath("$.model").value(MODEL)).andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.failures", hasSize(0)))
                // the rule is added at the end, under the description as a comment
                .andExpect(jsonPath("$.source", containsString(
                        "// Avvisa se le battute sono più di 4\ncontext T!ExerciseSpec {\n\n  critique TooManyBars {")))
                // Giulia's intent has eight bars: the new rule would report it
                .andExpect(jsonPath("$.effects.models").value(1))
                .andExpect(jsonPath("$.effects.reports[0].modelName").value("giulia.intent"))
                .andExpect(jsonPath("$.effects.reports[0].message").value("L'esercizio ha più di 4 battute"));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).path("model").asText()).isEqualTo(MODEL);
        assertThat(message(0, 0)).contains("Epsilon Validation Language");
        assertThat(message(0, 1)).contains("Script: intent.evl")
                // classes under the names of the models in the script, with the model the intent refers to
                .contains("class T!ExerciseSpec").contains("bars : EInt").contains("learner : L!Learner")
                .contains("class L!HandSpanLimit extends Need").contains("enum T!PatternKind { RHYTHM, ")
                // tools and operations the rule may call, and rules of the script as examples
                .contains("intervals is a IntervalTool:").contains("intervals.maxLeap(Object) : int").contains("operation T!ExerciseSpec hasLearner() : Boolean")
                .contains("critique ").contains("Names already taken: TempoAboveComfort")
                .endsWith("Avvisa se le battute sono più di 4\n");
        // nothing is saved: the script is still the original
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$[1].modified").value(false));
    }

    @Test
    void asksAgainWithTheErrorsOfTheParserAndOfTheTrialRun() throws Exception {
        answers.add("```\ncontext T!ExerciseSpec {\n  critique TooManyBars {\n    check self.bars <= 4\n  }\n}\n```");
        answers.add("```\n" + TOO_MANY_BARS.replace("self.bars", "self.measures") + "\n```");
        answers.add("```\n" + TOO_MANY_BARS + "\n```");
        propose("intent.evl", "Avvisa se le battute sono più di 4").andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(3)).andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.failures", hasSize(0))).andExpect(jsonPath("$.rule").value(TOO_MANY_BARS));

        assertThat(requests).hasSize(3);
        // the second request carries the first answer and its syntax error, counted from the rule
        assertThat(message(1, 2)).contains("check self.bars");
        assertThat(message(1, 3)).contains("Syntax error at line 3 of your rule");
        // the third one the error found by running the script on Giulia's intent
        assertThat(message(2, 5)).contains("Error when the script runs on a model: Property 'measures' not found");
    }

    @Test
    void returnsTheBestProposalWithItsErrorsWhenNoneIsSound() throws Exception {
        for (int i = 0; i < 3; i++) {
            answers.add("```\n" + TOO_MANY_BARS.replace("self.bars", "self.measures") + "\n```");
        }
        propose("intent.evl", "Avvisa se le battute sono più di 4").andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(3))
                .andExpect(jsonPath("$.failures[0].message", containsString("Property 'measures' not found")))
                .andExpect(jsonPath("$.effects").doesNotExist());
        assertThat(requests).hasSize(3);
    }

    @Test
    void refusesANameAlreadyTakenAndATransformationRuleThatWritesInAnInput() throws Exception {
        answers.add("```\n" + TOO_MANY_BARS.replace("TooManyBars", "TempoAboveComfort") + "\n```");
        answers.add("```\n" + TOO_MANY_BARS + "\n```");
        propose("intent.evl", "Avvisa se le battute sono più di 4").andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(2));
        assertThat(message(1, 3)).contains("The name TempoAboveComfort is already used");

        reset();
        String rule = """
                rule CopyTeacherSpan
                  transform c : T!SpanConstraint
                  to d : %s!SpanConstraint {

                  guard: c.eContainer() = intent()

                  ids.numbered(d, "c_span_teacher");
                  d.hand = c.hand;
                  spec().constraints.add(d);
                }""";
        answers.add("```etl\n" + rule.formatted("T") + "\n```");
        answers.add("```etl\n" + rule.formatted("S") + "\n```");
        propose("derive.etl", "Copia i vincoli di apertura della mano della docente").andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").value(2)).andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.failures", hasSize(0)))
                .andExpect(jsonPath("$.rule", containsString("to d : S!SpanConstraint")));
        assertThat(message(0, 0)).contains("Epsilon Transformation Language");
        assertThat(message(0, 1)).contains("Models T (read only), S (written by the script)")
                .contains("ids.numbered(Object, String) : String").doesNotContain("afterDerivation").contains("operation spec() : S!ExerciseSpec").contains("rule ");
        assertThat(message(1, 3)).contains("creates elements in T, which is read only: create them in S or W");
    }

    @Test
    void saysWhatToDoWhenItCannotAnswer() throws Exception {
        propose("intent.evl", "  ").andExpect(status().isBadRequest());
        propose("profile.evl", "Una regola").andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/assistant/rules").contentType(MediaType.APPLICATION_JSON)
                .content("{\"script\":\"nope.evl\",\"source\":\"\",\"description\":\"x\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/assistant")).andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.model").value(MODEL));
        installedModels = List.of();
        mvc.perform(get("/api/assistant")).andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason", containsString("ollama pull " + MODEL)));
        propose("intent.evl", "Avvisa se le battute sono più di 4").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("ollama pull " + MODEL)));
    }

    @Test
    void takesTheRuleOutOfTheAnswer() {
        assertThat(RuleExtractor.extract("Testo\n```evl\ncontext A!B {\n}\n```\naltro", RuleLanguage.EVL))
                .isEqualTo(Optional.of("context A!B {\n}"));
        assertThat(RuleExtractor.extract("context A!B {\n}", RuleLanguage.EVL)).isPresent();
        assertThat(RuleExtractor.extract("```\n@lazy\nrule X transform a : A!B to c : C!D {}\n```", RuleLanguage.ETL))
                .isPresent();
        assertThat(RuleExtractor.extract("```\npost X { }\n```", RuleLanguage.ETL)).isPresent();
        assertThat(RuleExtractor.extract("Non so scrivere questa regola.", RuleLanguage.EVL)).isEmpty();
        assertThat(RuleExtractor.extract("```\ncontext A!B { }\n```", RuleLanguage.ETL)).isEmpty();
        assertThat(RuleExtractor.extract("", RuleLanguage.EVL)).isEmpty();
        assertThat(RuleExtractor.extract(null, RuleLanguage.EVL)).isEmpty();
    }
}
