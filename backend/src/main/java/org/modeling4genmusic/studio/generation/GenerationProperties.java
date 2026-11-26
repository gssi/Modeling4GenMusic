package org.modeling4genmusic.studio.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param defaultGenerator generator used when a request names none
 * @param maxAttempts      how many exercises to ask for before giving back the best rejected one
 */
@ConfigurationProperties(prefix = "studio.generation")
public record GenerationProperties(@DefaultValue("mock") String defaultGenerator, @DefaultValue("3") int maxAttempts) {
}
