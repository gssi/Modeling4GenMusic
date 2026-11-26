package org.modeling4genmusic.studio.derivation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.xmi.XMLResource;

/**
 * Lets a transformation give its output elements stable, readable ids (available as the {@code ids}
 * variable). Ids are remembered while the script runs and written to the resources at the end, when
 * every element has reached its final place.
 */
public class ElementIdTool implements DerivationHook {

    private final Map<EObject, String> wanted = new LinkedHashMap<>();
    private final Set<String> used = new HashSet<>();
    private final Map<String, Integer> counters = new HashMap<>();

    /** Uses the given id, with a numeric suffix only if it is already taken. */
    public String exact(Object element, String id) {
        String candidate = id;
        for (int i = 2; !used.add(candidate); i++) {
            candidate = id + "_" + i;
        }
        wanted.put((EObject) element, candidate);
        return candidate;
    }

    /** Uses {@code base_1}, {@code base_2}, ... in the order of the calls. */
    public String numbered(Object element, String base) {
        String candidate;
        do {
            candidate = base + "_" + counters.merge(base, 1, Integer::sum);
        } while (!used.add(candidate));
        wanted.put((EObject) element, candidate);
        return candidate;
    }

    /** Id already chosen for an element, or null. */
    public String of(Object element) {
        return wanted.get(element);
    }

    @Override
    public void afterDerivation() {
        wanted.forEach((element, id) -> {
            if (element.eResource() instanceof XMLResource resource) {
                resource.setID(element, id);
            }
        });
    }
}
