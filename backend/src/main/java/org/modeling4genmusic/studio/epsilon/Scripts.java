package org.modeling4genmusic.studio.epsilon;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.epsilon.emc.emf.InMemoryEmfModel;
import org.eclipse.epsilon.eol.IEolModule;
import org.eclipse.epsilon.eol.execute.context.Variable;
import org.springframework.core.io.ClassPathResource;

/** Shared plumbing for running the Epsilon scripts kept in the {@code epsilon/} resource folder. */
public final class Scripts {

    public static final String FOLDER = "epsilon/";

    private Scripts() {
    }

    public static InputStream open(String name) throws IOException {
        return new ClassPathResource(FOLDER + name).getInputStream();
    }

    public static String read(String name) {
        try (InputStream in = open(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read script " + name, e);
        }
    }

    /** Parses a script into the module, failing on syntax errors. */
    public static void parse(IEolModule module, String name) {
        try {
            module.parse(read(name));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse " + name, e);
        }
        if (!module.getParseProblems().isEmpty()) {
            throw new IllegalStateException("Syntax errors in " + name + ": " + module.getParseProblems());
        }
    }

    /** A model that covers exactly one resource, not the other resources of its resource set. */
    public static InMemoryEmfModel model(String name, Resource resource, Collection<EPackage> packages) {
        return new InMemoryEmfModel(name, resource, new ArrayList<>(packages), false, false);
    }

    /**
     * Creates the helper objects a script can call and publishes them as read-only global variables.
     * They are plain instances looked up by the application class loader.
     *
     * @param tools variable name to class with a public no-argument constructor (may be null)
     */
    public static Map<String, Object> publishTools(IEolModule module, Map<String, String> tools) {
        Map<String, Object> instances = new LinkedHashMap<>();
        if (tools != null) {
            tools.forEach((name, className) -> {
                Object tool = tool(className);
                instances.put(name, tool);
                module.getContext().getFrameStack().putGlobal(Variable.createReadOnlyVariable(name, tool));
            });
        }
        return instances;
    }

    private static Object tool(String className) {
        try {
            return Class.forName(className, true, Scripts.class.getClassLoader()).getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot create the script tool " + className, e);
        }
    }
}
