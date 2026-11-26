package org.modeling4genmusic.studio.generation;

import java.util.List;

/**
 * Outcome of checking a generated exercise against its spec.
 *
 * @param valid  true when no blocking check failed
 * @param checks one entry per verified constraint
 */
public record ValidationReport(boolean valid, List<Check> checks) {

    /**
     * @param name     kind of check (bars, range, containsPattern, excludesPattern, abc)
     * @param passed   whether the exercise satisfies it
     * @param blocking false for preferences (soft constraints) and for checks that could not be run
     * @param message  explanation for the user
     */
    public record Check(String name, boolean passed, boolean blocking, String message) {
    }

    public static ValidationReport of(List<Check> checks) {
        return new ValidationReport(checks.stream().allMatch(c -> c.passed() || !c.blocking()), List.copyOf(checks));
    }
}
