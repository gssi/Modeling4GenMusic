package org.modeling4genmusic.studio.generation;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EObject;
import org.springframework.stereotype.Component;

/** Stand-in for a real generator: always returns the same short exercise, whatever the spec. */
@Component
public class MockExerciseGenerator implements ExerciseGenerator {

    static final String ABC = """
            X:1
            T:Esercizio croma puntata
            M:4/4
            L:1/8
            Q:1/4=72
            K:G
            G2 B2 d2 B2 | c>B A2 G2 | A2 B2 c2 A2 | B>A G2 D2 |
            E2 G2 B2 d2 | e>d c2 B2 | A2 B>A G2 | G8 |]
            """;

    @Override
    public GenerationResult generate(EObject exerciseSpec, GenerationContext ctx) {
        AbcTune tune = AbcTune.parse(ABC);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("generator", "mock");
        metadata.put("title", tune.headers().get("T"));
        metadata.put("meter", tune.headers().get("M"));
        metadata.put("key", tune.headers().get("K"));
        metadata.put("tempo", tune.headers().get("Q"));
        metadata.put("bars", tune.bars().size());
        return new GenerationResult(ABC, metadata);
    }
}
