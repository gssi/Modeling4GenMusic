package org.modeling4genmusic.studio.api;

import java.util.List;

import org.modeling4genmusic.studio.validation.RuleCatalog;
import org.modeling4genmusic.studio.validation.RuleSet;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The validation rules: reading, checking a new text of a script, keeping it, going back to the
 * original. The workspace is the one whose models a new text is tried on.
 */
@RestController
@RequestMapping("/api/rules")
public class RuleController {

    private static final String WS = "workspaceId";
    private static final String DEFAULT_WS = "default";

    private final RuleCatalog catalog;

    public RuleController(RuleCatalog catalog) {
        this.catalog = catalog;
    }

    public record ScriptText(String source) {
    }

    @GetMapping
    public List<RuleSet> list() {
        return catalog.ruleSets();
    }

    @PostMapping("/{script}/check")
    public RuleSet check(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String script, @RequestBody ScriptText text) {
        return catalog.check(workspaceId, script, text.source());
    }

    @PutMapping("/{script}")
    public RuleSet save(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String script, @RequestBody ScriptText text) {
        return catalog.save(workspaceId, script, text.source());
    }

    @DeleteMapping("/{script}")
    public RuleSet reset(@PathVariable String script) {
        return catalog.reset(script);
    }
}
