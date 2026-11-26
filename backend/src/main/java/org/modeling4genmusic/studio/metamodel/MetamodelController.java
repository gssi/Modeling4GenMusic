package org.modeling4genmusic.studio.metamodel;

import java.util.List;

import org.modeling4genmusic.studio.api.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metamodels")
public class MetamodelController {

    private final MetamodelRegistry registry;

    public MetamodelController(MetamodelRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<MetamodelDescriptor.Summary> list() {
        return registry.summaries();
    }

    @GetMapping("/{prefix}")
    public MetamodelDescriptor get(@PathVariable String prefix) {
        return registry.byPrefix(prefix).map(registry::describe)
                .orElseThrow(() -> ApiException.notFound("Unknown metamodel prefix: " + prefix));
    }
}
