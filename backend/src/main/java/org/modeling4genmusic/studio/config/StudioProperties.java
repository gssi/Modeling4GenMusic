package org.modeling4genmusic.studio.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Locations of the metamodels and of the runtime workspace, and size of the model history. Paths are bound as plain strings:
 * in a web application Spring would resolve a relative {@code Path} against the servlet context.
 */
@ConfigurationProperties(prefix = "studio")
public record StudioProperties(String metamodelsDir, String workspaceDir, Integer historySize) {

    private static final int DEFAULT_HISTORY_SIZE = 50;

    public static StudioProperties of(Path metamodelsDir, Path workspaceDir) {
        return new StudioProperties(metamodelsDir.toString(), workspaceDir.toString(), null);
    }

    /** How many versions of each model are kept, the current one included. */
    public int historyLimit() {
        return historySize == null || historySize < 1 ? DEFAULT_HISTORY_SIZE : historySize;
    }

    public Path metamodelsPath() {
        return Path.of(metamodelsDir);
    }

    public Path workspacePath() {
        return Path.of(workspaceDir);
    }
}
