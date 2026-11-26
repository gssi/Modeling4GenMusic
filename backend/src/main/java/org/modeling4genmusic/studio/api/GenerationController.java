package org.modeling4genmusic.studio.api;

import org.modeling4genmusic.studio.generation.GenerationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Generation of an exercise from a spec, followed by its validation. */
@RestController
@RequestMapping("/api/generations")
public class GenerationController {

    private final GenerationService service;

    public GenerationController(GenerationService service) {
        this.service = service;
    }

    public record GenerationRequest(String specModelId, String qualityModelId) {
    }

    @PostMapping
    public GenerationService.Generation generate(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody GenerationRequest request) {
        if (request.specModelId() == null || request.specModelId().isBlank()) {
            throw ApiException.badRequest("Manca il campo specModelId");
        }
        return service.generate(workspaceId, request.specModelId(), request.qualityModelId());
    }
}
