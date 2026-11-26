package org.modeling4genmusic.studio.generation;

import org.eclipse.emf.ecore.EObject;

/** Produces an exercise from a complete exercise spec. */
public interface ExerciseGenerator {

    GenerationResult generate(EObject exerciseSpec, GenerationContext ctx);
}
