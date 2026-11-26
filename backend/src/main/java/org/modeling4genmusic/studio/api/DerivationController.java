package org.modeling4genmusic.studio.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.modeling4genmusic.studio.derivation.DerivationService;
import org.modeling4genmusic.studio.derivation.TransformationScript;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Derivation of new models from existing ones. Request and response fields are named by the descriptor. */
@RestController
@RequestMapping("/api/derivations")
public class DerivationController {

    private final DerivationService service;

    public DerivationController(DerivationService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, Object> derive(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody Map<String, String> request) {
        DerivationService.Result result = service.derive(workspaceId, request);
        Map<String, Object> response = new LinkedHashMap<>(result.outputs());
        response.put("diagnostics", result.diagnostics());
        return response;
    }

    public record ScriptText(String source) {
    }

    /** The transformation script, described for reading. */
    @GetMapping("/script")
    public TransformationScript script() {
        return service.script();
    }

    @PostMapping("/script/check")
    public TransformationScript check(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody ScriptText text) {
        return service.check(workspaceId, text.source());
    }

    @PutMapping("/script")
    public TransformationScript save(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody ScriptText text) {
        return service.save(workspaceId, text.source());
    }

    @DeleteMapping("/script")
    public TransformationScript reset() {
        return service.reset();
    }
}
