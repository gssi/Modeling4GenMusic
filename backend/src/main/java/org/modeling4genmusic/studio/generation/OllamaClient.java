package org.modeling4genmusic.studio.generation;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.modeling4genmusic.studio.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Minimal client of the Ollama HTTP API: which models are there, and plain text completion. */
@Component
public class OllamaClient {

    private final OllamaProperties properties;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public OllamaClient(OllamaProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    public boolean feedback() {
        return properties.feedback();
    }

    public String model() {
        return properties.model();
    }

    /** Asks the server whether it is up and has the configured model. Never throws. */
    public ExerciseGenerator.Availability availability() {
        String model = properties.model();
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri("/api/tags")).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ExerciseGenerator.Availability.missing(model, "Ollama risponde con un errore (" + response.statusCode() + ")");
            }
            for (JsonNode entry : json.readTree(response.body()).path("models")) {
                if (model.equals(entry.path("name").asText()) || model.equals(entry.path("model").asText())) {
                    return ExerciseGenerator.Availability.ready(model);
                }
            }
            return ExerciseGenerator.Availability.missing(model,
                    "Il modello non è stato scaricato: esegui «ollama pull " + model + "»");
        } catch (IOException | IllegalArgumentException e) {
            return ExerciseGenerator.Availability.missing(model,
                    "Ollama non risponde su " + properties.baseUrl() + ": avvialo con «ollama serve»");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExerciseGenerator.Availability.missing(model, "Richiesta a Ollama interrotta");
        }
    }

    /**
     * Completes the prompt as it is, without any chat template.
     *
     * @param seed      makes the answer repeatable; change it to get a different one
     * @param maxTokens upper bound of the answer length
     */
    public String complete(String prompt, long seed, int maxTokens) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", properties.temperature());
        options.put("top_k", 40);
        options.put("top_p", 0.9);
        options.put("repeat_penalty", 1.1);
        options.put("num_predict", maxTokens);
        options.put("seed", seed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put("prompt", prompt);
        body.put("raw", true);
        body.put("stream", false);
        body.put("options", options);
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(uri("/api/generate"))
                    .timeout(properties.timeout()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode answer = json.readTree(response.body());
            if (response.statusCode() != 200) {
                throw unavailable("Ollama non ha generato l'esercizio: " + answer.path("error").asText("errore " + response.statusCode()));
            }
            return answer.path("response").asText("");
        } catch (java.net.http.HttpTimeoutException e) {
            throw unavailable("Ollama non ha risposto entro " + properties.timeout().toSeconds() + " secondi");
        } catch (IOException | IllegalArgumentException e) {
            throw unavailable("Ollama non risponde su " + properties.baseUrl() + ": avvialo con «ollama serve»");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Richiesta a Ollama interrotta");
        }
    }

    private URI uri(String path) {
        return URI.create(properties.baseUrl().replaceAll("/+$", "") + path);
    }

    private static ApiException unavailable(String message) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
