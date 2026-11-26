package org.modeling4genmusic.studio.assistant;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The language model that turns a description into a rule. It is served by the same Ollama server
 * as the music model ({@code studio.ollama.base-url}).
 *
 * @param model       name of the model, as listed by {@code ollama list}
 * @param timeout     longest wait for one answer
 * @param temperature sampling temperature; low values keep the answer close to the examples
 * @param maxAttempts how many times a rule is asked for, each time with the errors of the previous one
 */
@ConfigurationProperties(prefix = "studio.assistant")
public record AssistantProperties(@DefaultValue("qwen2.5-coder:14b") String model,
        @DefaultValue("120s") Duration timeout, @DefaultValue("0.1") double temperature,
        @DefaultValue("3") int maxAttempts) {
}
