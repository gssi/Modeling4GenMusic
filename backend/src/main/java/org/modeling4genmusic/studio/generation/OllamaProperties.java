package org.modeling4genmusic.studio.generation;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Connection to the Ollama server that runs the music model.
 *
 * @param baseUrl     address of the server
 * @param model       name of the model, as listed by {@code ollama list}
 * @param timeout     longest wait for one exercise
 * @param temperature sampling temperature; low values keep the model close to the request
 * @param feedback    whether a new attempt tells the model which checks the previous exercise failed;
 *                    off by default because, with the model in use, it did not make valid exercises more likely
 */
@ConfigurationProperties(prefix = "studio.ollama")
public record OllamaProperties(@DefaultValue("http://localhost:11434") String baseUrl,
        @DefaultValue("hf.co/mradermacher/ChatMusician-GGUF:Q4_K_M") String model,
        @DefaultValue("120s") Duration timeout, @DefaultValue("0.2") double temperature,
        @DefaultValue("false") boolean feedback) {
}
