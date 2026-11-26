package org.modeling4genmusic.studio.derivation;

/** A script tool that has work to do once the transformation has finished, before the outputs are saved. */
public interface DerivationHook {

    void afterDerivation();
}
