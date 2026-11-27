package org.modeling4genmusic.studio.api;

import java.io.IOException;
import java.util.List;

import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.workspace.ModelVersion;
import org.modeling4genmusic.studio.workspace.WorkspaceStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;

/** CRUD of the workspace models. The workspace id is a query parameter, fixed to "default" for now. */
@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final String WS = "workspaceId";
    private static final String DEFAULT_WS = "default";

    private final WorkspaceStore store;

    public ModelController(WorkspaceStore store) {
        this.store = store;
    }

    public record CreateRequest(String metamodel, String name, String role, String folder) {
    }

    @GetMapping
    public List<ModelDocument> list(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @RequestParam(required = false) String metamodel) {
        return store.list(workspaceId, metamodel);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ModelDocument create(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @RequestBody CreateRequest request) {
        return store.create(workspaceId, request.metamodel(), request.name(), request.role(), request.folder());
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ModelDocument importXmi(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @RequestParam("file") MultipartFile file, @RequestParam(required = false) String name,
            @RequestParam(required = false) String role, @RequestParam(required = false) String folder)
            throws IOException {
        return store.importXmi(workspaceId, file.getOriginalFilename(), file.getBytes(), name, role, folder);
    }

    @GetMapping("/{id}")
    public ModelDocument get(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id) {
        return store.get(workspaceId, id);
    }

    @PutMapping("/{id}")
    public ModelDocument replace(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id, @RequestBody ModelDocument document) {
        return store.replace(workspaceId, id, document);
    }

    /** {@code folder} blank or missing takes the model out of every folder. */
    public record FolderRequest(String folder) {
    }

    /** Moves a model to a folder of the workspace. The content is not touched: no new version. */
    @PutMapping("/{id}/folder")
    public ModelDocument move(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id, @RequestBody FolderRequest request) {
        return store.move(workspaceId, id, request.folder());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id) {
        store.delete(workspaceId, id);
    }

    @GetMapping("/{id}/layout")
    public JsonNode layout(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id) {
        return store.layout(workspaceId, id);
    }

    @PutMapping("/{id}/layout")
    public JsonNode saveLayout(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id, @RequestBody JsonNode layout) {
        return store.saveLayout(workspaceId, id, layout);
    }

    @GetMapping("/{id}/versions")
    public List<ModelVersion> versions(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id) {
        return store.versions(workspaceId, id);
    }

    @GetMapping("/{id}/versions/{version}")
    public ModelDocument version(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id, @PathVariable int version) {
        return store.version(workspaceId, id, version);
    }

    @PostMapping("/{id}/versions/{version}/restore")
    public ModelDocument restore(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id, @PathVariable int version) {
        return store.restore(workspaceId, id, version);
    }

    @GetMapping(path = "/{id}/xmi")
    public ResponseEntity<byte[]> exportXmi(@RequestParam(name = WS, defaultValue = DEFAULT_WS) String workspaceId,
            @PathVariable String id) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".xmi\"")
                .body(store.exportXmi(workspaceId, id));
    }
}
