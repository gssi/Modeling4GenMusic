package org.modeling4genmusic.studio.generation;

import org.eclipse.emf.ecore.EObject;

/** Produces an exercise from a complete exercise spec. */
public interface ExerciseGenerator {

    /** Name used to choose this generator in a request and in the configuration. */
    String id();

    /** Name shown to the user. */
    String label();

    /** Whether the generator can be used right now, and why not otherwise. */
    default Availability availability() {
        return Availability.ready(null);
    }

    /** Whether asking again can give a different exercise, so that a rejected one is worth retrying. */
    default boolean varies() {
        return false;
    }

    GenerationResult generate(EObject exerciseSpec, GenerationContext ctx);

    /**
     * @param available whether a request would be served
     * @param model     the underlying model, when there is one
     * @param reason    what to do when it is not available, for the user
     */
    record Availability(boolean available, String model, String reason) {

        public static Availability ready(String model) {
            return new Availability(true, model, null);
        }

        public static Availability missing(String model, String reason) {
            return new Availability(false, model, reason);
        }
    }
}
