package org.modeling4genmusic.studio.generation;

import java.util.Map;

/**
 * A generated exercise.
 *
 * @param abc      the music, in ABC notation
 * @param metadata facts about the result (generator, title, meter, key, tempo, bars)
 */
public record GenerationResult(String abc, Map<String, Object> metadata) {
}
