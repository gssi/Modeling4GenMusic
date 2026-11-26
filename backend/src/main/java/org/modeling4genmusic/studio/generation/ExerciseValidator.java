package org.modeling4genmusic.studio.generation;

import org.eclipse.emf.ecore.EObject;

/** Checks a generated exercise against the spec it was generated from. */
public interface ExerciseValidator {

    /**
     * @param qualityModel thresholds for the quality goals of the spec (may be null)
     */
    ValidationReport validate(String abc, EObject exerciseSpec, EObject qualityModel);
}
