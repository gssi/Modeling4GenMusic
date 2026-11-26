package org.modeling4genmusic.studio.api;

import java.util.List;

import org.modeling4genmusic.studio.validation.Diagnostic;
import org.modeling4genmusic.studio.validation.ValidationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Validation of a workspace model and application of the fixes offered by its diagnostics. */
@RestController
@RequestMapping("/api/models")
public class ValidationController {

    private final ValidationService service;

    public ValidationController(ValidationService service) {
        this.service = service;
    }

    public record FixRequest(String diagnosticId, Integer fixIndex) {
    }

    @PostMapping("/{id}/validate")
    public List<Diagnostic> validate(@RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @PathVariable String id) {
        return service.validate(workspaceId, id);
    }

    @PostMapping("/{id}/fixes")
    public ValidationService.FixResult applyFix(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @PathVariable String id, @RequestBody FixRequest request) {
        if (request.diagnosticId() == null || request.fixIndex() == null) {
            throw ApiException.badRequest("diagnosticId and fixIndex are required");
        }
        return service.applyFix(workspaceId, id, request.diagnosticId(), request.fixIndex());
    }
}
