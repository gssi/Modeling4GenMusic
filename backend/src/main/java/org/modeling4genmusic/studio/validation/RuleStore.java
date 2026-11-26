package org.modeling4genmusic.studio.validation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.epsilon.Scripts;
import org.springframework.stereotype.Component;

/**
 * Where the text of a validation or transformation script comes from. The scripts shipped with the application are the
 * originals; one edited from the editor is kept as a file in {@code .rules/} at the root of the
 * workspace folder and takes the place of the original until it is removed.
 */
@Component
public class RuleStore {

    private static final String FOLDER = ".rules";

    private final Path dir;

    public RuleStore(StudioProperties properties) {
        this.dir = properties.workspacePath().toAbsolutePath().normalize().resolve(FOLDER);
    }

    public synchronized String source(String script) {
        Path file = file(script);
        if (!Files.exists(file)) {
            return Scripts.read(script);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized boolean isModified(String script) {
        return Files.exists(file(script));
    }

    public synchronized void save(String script, String source) {
        try {
            Files.createDirectories(dir);
            Files.writeString(file(script), source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Goes back to the original script. */
    public synchronized void reset(String script) {
        try {
            Files.deleteIfExists(file(script));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String script) {
        if (script == null || !script.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid script name: " + script);
        }
        return dir.resolve(script);
    }
}
