package org.modeling4genmusic.studio.validation;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        for (String file : new String[] { "giulia.profile.xmi", "giulia.intent.xmi", "piano.quality.xmi" }) {
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
}
