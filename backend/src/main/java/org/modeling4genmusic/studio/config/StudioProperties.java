package org.modeling4genmusic.studio.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Locations of the metamodels and of the runtime workspace. They are bound as plain strings:
 * in a web application Spring would resolve a relative {@code Path} against the servlet context.
 */
@ConfigurationProperties(prefix = "studio")
public record StudioProperties(String metamodelsDir, String workspaceDir) {

    public static StudioProperties of(Path metamodelsDir, Path workspaceDir) {
        return new StudioProperties(metamodelsDir.toString(), workspaceDir.toString());
    }

    public Path metamodelsPath() {
        return Path.of(metamodelsDir);
    }

    public Path workspacePath() {
        return Path.of(workspaceDir);
    }
}
