package org.modeling4genmusic.studio.api;

import org.modeling4genmusic.studio.assistant.RuleAssistant;
import org.modeling4genmusic.studio.generation.ExerciseGenerator.Availability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Help in writing rules: a description in plain words becomes a rule proposed for a script. */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final RuleAssistant assistant;

    public AssistantController(RuleAssistant assistant) {
        this.assistant = assistant;
    }

    /**
     * @param script      name of the script the rule is for
     * @param source      current text of the script
     * @param description what the rule must do
     */
    public record RuleRequest(String script, String source, String description) {
    }

    @GetMapping
    public Availability availability() {
        return assistant.availability();
    }

    @PostMapping("/rules")
    public RuleAssistant.Proposal propose(
            @RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId,
            @RequestBody RuleRequest request) {
        return assistant.propose(workspaceId, request.script(), request.source(), request.description());
    }
}
