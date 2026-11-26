package org.modeling4genmusic.studio.api;

import java.util.List;

import org.modeling4genmusic.studio.generation.GenerationService;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** {@code generator} is optional: the configured default is used when it is missing. */
    public record GenerationRequest(String specModelId, String qualityModelId, String generator) {
    }

    /** The generators that can be asked for an exercise, and whether each one is ready. */
    @GetMapping("/generators")
    public List<GenerationService.GeneratorInfo> generators() {
        return service.generators();
    }

    @PostMapping
    public GenerationService.Generation generate(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody GenerationRequest request) {
        if (request.specModelId() == null || request.specModelId().isBlank()) {
            throw ApiException.badRequest("Manca il campo specModelId");
        }
        return service.generate(workspaceId, request.specModelId(), request.qualityModelId(), request.generator());
    }
}
