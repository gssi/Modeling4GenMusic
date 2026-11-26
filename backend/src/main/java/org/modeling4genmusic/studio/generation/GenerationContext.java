package org.modeling4genmusic.studio.generation;

import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.modeling4genmusic.studio.generation.ValidationReport.Check;

/**
 * What a generator may use besides the spec.
 *
 * @param qualityModel quality model that gives meaning to the quality goals of the spec (may be null)
 * @param attempt      1 for the first request, then 2, 3… when earlier exercises were rejected
 * @param rejected     blocking checks the previous exercise failed (empty on the first attempt)
 */
public record GenerationContext(EObject qualityModel, int attempt, List<Check> rejected) {

    public GenerationContext(EObject qualityModel) {
        this(qualityModel, 1, List.of());
    }
}
