package org.modeling4genmusic.studio.generation;

import org.eclipse.emf.ecore.EObject;

/**
 * What a generator may use besides the spec.
 *
 * @param qualityModel quality model that gives meaning to the quality goals of the spec (may be null)
 */
public record GenerationContext(EObject qualityModel) {
}
