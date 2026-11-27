package org.modeling4genmusic.studio.validation;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Acceptance of the validation milestone on the Giulia fixtures. */
@SpringBootTest
@AutoConfigureMockMvc
class ValidationApiTest {

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("studio.workspace-dir", () -> workspace.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void giuliaIntentWarnsAboutTempoAndTheFixResolvesIt() throws Exception {
        for (String file : new String[] { "default.needs.xmi", "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : "")).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/models/giulia.profile/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(post("/api/models/piano.quality/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        String diagnosticId = "giulia.intent#TempoAboveComfort@intent";
        mvc.perform(post("/api/models/giulia.intent/validate")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(diagnosticId))
                .andExpect(jsonPath("$[0].modelId").value("giulia.intent"))
                .andExpect(jsonPath("$[0].elementId").value("intent"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"))
                .andExpect(jsonPath("$[0].constraint").value("TempoAboveComfort"))
                .andExpect(jsonPath("$[0].fixes", hasSize(1)))
                .andExpect(jsonPath("$[0].fixes[0].title").value("Limita il tempo a 72 bpm"));

        String fix = "{\"diagnosticId\":\"" + diagnosticId + "\",\"fixIndex\":0}";
        mvc.perform(post("/api/models/giulia.intent/fixes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"diagnosticId\":\"" + diagnosticId + "\",\"fixIndex\":3}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/models/giulia.intent/fixes").contentType(MediaType.APPLICATION_JSON).content(fix))
                .andExpect(status().isOk()).andExpect(jsonPath("$.model.version").value(2))
                .andExpect(jsonPath("$.model.roots[0].attributes.maxBpm").value(72))
                .andExpect(jsonPath("$.model.roots[0].attributes.requestedBpm").value(90))
                .andExpect(jsonPath("$.diagnostics", hasSize(0)));

        mvc.perform(get("/api/models/giulia.intent")).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.roots[0].attributes.maxBpm").value(72))
                .andExpect(jsonPath("$.roots[0].references.learner[0].model").value("giulia.profile"));
        mvc.perform(post("/api/models/giulia.intent/validate")).andExpect(jsonPath("$", hasSize(0)));
        // The diagnostic is gone: asking for its fix again is a 404.
        mvc.perform(post("/api/models/giulia.intent/fixes").contentType(MediaType.APPLICATION_JSON).content(fix))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/models/nope/validate")).andExpect(status().isNotFound());
    }

    @Test
    void listsTheValidationRulesForReading() throws Exception {
        mvc.perform(get("/api/rules")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].script", contains("profile.evl", "intent.evl", "quality.evl", "weaving.evl", "needs.evl")))
                .andExpect(jsonPath("$[*].problems.length()", everyItem(is(0))))
                .andExpect(jsonPath("$[0].metamodel").value("http://music/profile"))
                .andExpect(jsonPath("$[0].contexts[*].rules[*].name",
                        hasItems("NameRequired", "RangeOrdered", "SkillLevelBounds", "PatternSyntax", "SlotValue")))
                .andExpect(jsonPath("$[0].tools", contains("intervals", "needs", "rhythm")))
                .andExpect(jsonPath("$[1].related[0].alias").value("L"))
                .andExpect(jsonPath("$[1].source", containsString("critique LeapBeyondReach")))
                .andExpect(jsonPath("$[3].contexts[*].rules[*].severity", hasItems("WARNING", "ERROR")));
    }

    @Test
    void editsAScriptOnlyWhenItParsesAndRunsAndGoesBackToTheOriginal() throws Exception {
        for (String file : new String[] { "default.needs.xmi", "giulia.profile.xmi", "giulia-leap.intent.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("name", "rules-" + file).param("role", file.contains("intent") ? "intent" : ""));
        }
        String original = org.modeling4genmusic.studio.epsilon.Scripts.read("intent.evl");
        java.util.function.Function<String, String> body = source -> {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("source", source));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };

        // a syntax error is reported with its position and nothing is saved
        String broken = original.replace("critique LeapBeyondReach {", "critique LeapBeyondReach");
        mvc.perform(post("/api/rules/intent.evl/check").contentType(MediaType.APPLICATION_JSON).content(body.apply(broken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.problems[0].line").value(55))
                .andExpect(jsonPath("$.contexts", hasSize(0)));
        mvc.perform(put("/api/rules/intent.evl").contentType(MediaType.APPLICATION_JSON).content(body.apply(broken)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message", containsString("line 55")));

        // right syntax, wrong property: only the trial run on a model can tell
        String wrong = original.replace("check: intervals.maxLeap(self.pattern.expression) <=", "check: self.noSuchThing <=");
        mvc.perform(post("/api/rules/intent.evl/check").contentType(MediaType.APPLICATION_JSON).content(body.apply(wrong)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.problems", hasSize(0)))
                .andExpect(jsonPath("$.failures[0].modelId").value("giulia-leap.intent"))
                .andExpect(jsonPath("$.failures[0].message")
                        .value("Property 'noSuchThing' not found in ContainsPattern (line 58)"));
        mvc.perform(put("/api/rules/intent.evl").contentType(MediaType.APPLICATION_JSON).content(body.apply(wrong)))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/rules")).andExpect(jsonPath("$[1].modified").value(false));

        // a sound change is kept and changes what validation says
        mvc.perform(post("/api/models/giulia-leap.intent/validate"))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].severity", contains("WARNING")));
        String stricter = original.replace("critique LeapBeyondReach", "constraint LeapBeyondReach");
        mvc.perform(put("/api/rules/intent.evl").contentType(MediaType.APPLICATION_JSON).content(body.apply(stricter)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.modified").value(true))
                .andExpect(jsonPath("$.source", containsString("constraint LeapBeyondReach")));
        assertThat(workspace.resolve(".rules/intent.evl")).exists();
        mvc.perform(post("/api/models/giulia-leap.intent/validate"))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].severity", contains("ERROR")));
        mvc.perform(get("/api/models")).andExpect(jsonPath("$[?(@.id=='.rules')]", hasSize(0)));

        // back to the script shipped with the application
        mvc.perform(delete("/api/rules/intent.evl")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modified").value(false));
        mvc.perform(post("/api/models/giulia-leap.intent/validate"))
                .andExpect(jsonPath("$[?(@.constraint=='LeapBeyondReach')].severity", contains("WARNING")));
        mvc.perform(put("/api/rules/nope.evl").contentType(MediaType.APPLICATION_JSON).content(body.apply(original)))
                .andExpect(status().isNotFound());
    }
}
