package org.modeling4genmusic.studio;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

@SpringBootTest
@AutoConfigureMockMvc
class ModelApiTest {

    @TempDir
    static Path workspace;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("studio.workspace-dir", () -> workspace.toString());
    }

    @Autowired
    MockMvc mvc;

    @Test
    void listsAndDescribesMetamodels() throws Exception {
        mvc.perform(get("/api/metamodels")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].prefix", containsInAnyOrder("pr", "ex", "qm", "wv")));
        mvc.perform(get("/api/metamodels/ex")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nsURI").value("http://music/exercise"))
                .andExpect(jsonPath("$.classes[?(@.name=='Constraint')].abstract").value(true))
                .andExpect(jsonPath("$.classes[?(@.name=='ContainsPattern')].supertypes[0].name")
                        .value("Constraint"))
                .andExpect(jsonPath("$.classes[?(@.name=='ContainsPattern')].attributes[*].name",
                        containsInAnyOrder("strength", "weight", "minOccurrences")))
                .andExpect(jsonPath("$.classes[?(@.name=='ExerciseSpec')].references[?(@.name=='learner')]"
                        + ".target.metamodel").value("http://music/profile"));
        mvc.perform(get("/api/metamodels/nope")).andExpect(status().isNotFound());
    }

    @Test
    void crudWithOptimisticLockAndLayout() throws Exception {
        mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON)
                .content("{\"metamodel\":\"http://music/profile\",\"name\":\"Crud Test\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("crud-test"))
                .andExpect(jsonPath("$.version").value(1));

        String body = """
                {"version":1,"roots":[{"id":"l","eClass":"Learner",
                  "attributes":{"name":"anna","instrument":"FLUTE","comfortableBpm":60}}]}""";
        mvc.perform(put("/api/models/crud-test").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.roots[0].attributes.instrument").value("FLUTE"));
        mvc.perform(put("/api/models/crud-test").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/models/crud-test/layout").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nodes\":{\"l\":{\"x\":10,\"y\":20}}}")).andExpect(status().isOk());
        mvc.perform(get("/api/models/crud-test/layout")).andExpect(jsonPath("$.nodes.l.x").value(10));

        mvc.perform(get("/api/models/crud-test/xmi")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"anna\"")));
        mvc.perform(get("/api/models").param("metamodel", "http://music/profile"))
                .andExpect(jsonPath("$[?(@.id=='crud-test')]", hasSize(1)));

        mvc.perform(delete("/api/models/crud-test")).andExpect(status().isNoContent());
        mvc.perform(get("/api/models/crud-test")).andExpect(status().isNotFound());
    }

    @Test
    void keepsTheHistoryOfAModelAndRestoresAPastVersion() throws Exception {
        mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON)
                .content("{\"metamodel\":\"http://music/profile\",\"name\":\"History Test\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("history-test"));
        String learner = """
                {"version":%d,"roots":[{"id":"l","eClass":"Learner",
                  "attributes":{"name":"anna","comfortableBpm":%d}}]}""";
        mvc.perform(put("/api/models/history-test").contentType(MediaType.APPLICATION_JSON)
                .content(learner.formatted(1, 60))).andExpect(status().isOk());
        mvc.perform(put("/api/models/history-test").contentType(MediaType.APPLICATION_JSON)
                .content(learner.formatted(2, 84))).andExpect(status().isOk());

        mvc.perform(get("/api/models/history-test/versions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].version", contains(3, 2, 1)))
                .andExpect(jsonPath("$[*].origin", contains("SAVED", "SAVED", "CREATED")))
                .andExpect(jsonPath("$[0].savedAt").isNotEmpty());
        mvc.perform(get("/api/models/history-test/versions/2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(60));
        mvc.perform(get("/api/models/history-test/versions/1")).andExpect(jsonPath("$.roots", hasSize(0)));
        mvc.perform(get("/api/models/history-test/versions/9")).andExpect(status().isNotFound());

        // restoring never rewrites the past: it adds a version with the old content
        mvc.perform(post("/api/models/history-test/versions/3/restore")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/models/history-test/versions/2/restore")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(60));
        mvc.perform(get("/api/models/history-test")).andExpect(jsonPath("$.version").value(4))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(60));
        mvc.perform(get("/api/models/history-test/versions"))
                .andExpect(jsonPath("$[*].version", contains(4, 3, 2, 1)))
                .andExpect(jsonPath("$[0].origin").value("RESTORED"))
                .andExpect(jsonPath("$[0].restoredFrom").value(2));
        mvc.perform(get("/api/models/history-test/versions/3"))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(84));

        // a model changed on disk, or saved before the history existed, is recorded when first asked
        Path xmi = workspace.resolve("default/history-test.xmi");
        Files.writeString(xmi, Files.readString(xmi).replace("comfortableBpm=\"60\"", "comfortableBpm=\"66\""));
        mvc.perform(get("/api/models/history-test/versions/4"))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(66));

        // the history goes away with the model and does not come back with a namesake
        mvc.perform(delete("/api/models/history-test")).andExpect(status().isNoContent());
        mvc.perform(get("/api/models/history-test/versions")).andExpect(status().isNotFound());
        mvc.perform(post("/api/models").contentType(MediaType.APPLICATION_JSON)
                .content("{\"metamodel\":\"http://music/profile\",\"name\":\"History Test\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/models/history-test/versions")).andExpect(jsonPath("$[*].version", contains(1)));
        mvc.perform(get("/api/models")).andExpect(jsonPath("$[?(@.id=='.history')]", hasSize(0)));
    }

    @Test
    void importsGiuliaFixtures() throws Exception {
        for (String file : new String[] { "giulia.intent.xmi", "giulia.profile.xmi", "piano.quality.xmi" }) {
            byte[] bytes = Files.readAllBytes(Path.of("../fixtures/giulia", file));
            mvc.perform(multipart("/api/models/import").file(new MockMultipartFile("file", file, "text/xml", bytes))
                    .param("role", file.contains("intent") ? "intent" : ""))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/models/giulia.profile")).andExpect(status().isOk())
                .andExpect(jsonPath("$.metamodel").value("http://music/profile"))
                .andExpect(jsonPath("$.roots[0].attributes.comfortableBpm").value(72))
                .andExpect(jsonPath("$.roots[0].contents.patterns", hasSize(4)))
                .andExpect(jsonPath("$.roots[0].contents.needs[0].eClass").value("LowVision"));
        mvc.perform(get("/api/models/giulia.intent")).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("intent"))
                .andExpect(jsonPath("$.roots[0].references.learner[0].model").value("giulia.profile"))
                .andExpect(jsonPath("$.roots[0].references.learner[0].id").value("giulia"))
                .andExpect(jsonPath("$.roots[0].references.learner[0].unresolved").doesNotExist())
                .andExpect(jsonPath("$.roots[0].contents.constraints[0].contents.pattern[0].attributes.expression")
                        .value("8. 16"));
        mvc.perform(get("/api/models/piano.quality"))
                .andExpect(jsonPath("$.roots[0].contents.bindings[1].contents.bands[0].id").value("m_low"));
        mvc.perform(multipart("/api/models/import")
                .file(new MockMultipartFile("file", "broken.xmi", "text/xml", "<nope".getBytes())))
                .andExpect(status().isBadRequest());
    }
}
