package org.modeling4genmusic.studio.workspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.modeling4genmusic.studio.api.ApiException;
import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;
import org.modeling4genmusic.studio.model.ModelDocument;
import org.modeling4genmusic.studio.model.ModelJsonMapper;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

/**
 * File-system persistence of the models. Each model {@code id} of a workspace is stored as
 * {@code id.xmi} (semantics only), {@code id.meta.json} (name, metamodel, version, role, folder) and
 * {@code id.layout.json} (diagram positions). References between models are relative hrefs.
 * <p>
 * Every save also leaves a copy in {@code .history/id/}: {@code N.xmi} with the content of version
 * {@code N} and {@code N.json} with its {@link ModelVersion}. Only the most recent ones are kept.
 */
@Component
public class WorkspaceStore {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");
    private static final int MAX_FOLDER_NAME = 60;
    private static final int MAX_FOLDER_DEPTH = 4;
    private static final Map<String, Object> XMI_OPTIONS = Map.of(XMLResource.OPTION_ENCODING, "UTF-8");
    private static final String HISTORY_DIR = ".history";

    private final Path root;
    private final MetamodelRegistry registry;
    private final ObjectMapper json;
    private final int historySize;

    public WorkspaceStore(StudioProperties properties, MetamodelRegistry registry, ObjectMapper json) {
        this.root = properties.workspacePath().toAbsolutePath().normalize();
        this.registry = registry;
        this.json = json;
        this.historySize = properties.historyLimit();
    }

    // ---------------------------------------------------------------- queries

    public synchronized List<ModelDocument> list(String workspaceId, String metamodel) {
        Path dir = dir(workspaceId);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<ModelDocument> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".meta.json")).toList()) {
                String name = file.getFileName().toString();
                String id = name.substring(0, name.length() - ".meta.json".length());
                ModelMeta meta = readMeta(workspaceId, id);
                if (metamodel == null || metamodel.equals(meta.metamodel())) {
                    result.add(header(id, meta));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort(Comparator.comparing(ModelDocument::name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized ModelDocument get(String workspaceId, String id) {
        ModelMeta meta = readMeta(workspaceId, id);
        XMLResource resource = load(workspaceId, id);
        return header(id, meta).withRoots(ModelJsonMapper.toJson(resource));
    }

    public synchronized ModelMeta meta(String workspaceId, String id) {
        return readMeta(workspaceId, id);
    }

    /** Loads a model in a fresh resource set that can resolve references to the other workspace models. */
    public synchronized XMLResource load(String workspaceId, String id) {
        requireExists(workspaceId, id);
        ResourceSet resourceSet = newResourceSet();
        try {
            return (XMLResource) resourceSet.getResource(uri(workspaceId, id), true);
        } catch (RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Model '" + id + "' cannot be read: " + rootMessage(e));
        }
    }

    public synchronized byte[] exportXmi(String workspaceId, String id) {
        requireExists(workspaceId, id);
        try {
            return Files.readAllBytes(xmiFile(workspaceId, id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized JsonNode layout(String workspaceId, String id) {
        requireExists(workspaceId, id);
        Path file = dir(workspaceId).resolve(id + ".layout.json");
        if (!Files.exists(file)) {
            return JsonNodeFactory.instance.objectNode();
        }
        try {
            return json.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- commands

    public synchronized ModelDocument create(String workspaceId, String metamodel, String name, String role) {
        return create(workspaceId, metamodel, name, role, null);
    }

    public synchronized ModelDocument create(String workspaceId, String metamodel, String name, String role,
            String folder) {
        EPackage ePackage = requirePackage(metamodel);
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("Model name is required");
        }
        String id = uniqueId(workspaceId, slug(name));
        XMLResource resource = (XMLResource) newResourceSet().createResource(uri(workspaceId, id));
        ModelMeta meta = new ModelMeta(name.strip(), ePackage.getNsURI(), 1, blankToNull(role), folderPath(folder));
        write(workspaceId, id, resource, meta, ModelVersion.CREATED, null);
        return header(id, meta).withRoots(List.of());
    }

    /** Replaces the content of a model; fails with 409 when the client edited a stale version. */
    public synchronized ModelDocument replace(String workspaceId, String id, ModelDocument document) {
        ModelMeta meta = readMeta(workspaceId, id);
        if (document.version() == null || document.version() != meta.version()) {
            throw ApiException.conflict("Model '" + id + "' was modified elsewhere (current version "
                    + meta.version() + ", received " + document.version() + ")");
        }
        EPackage ePackage = requirePackage(meta.metamodel());
        ResourceSet resourceSet = newResourceSet();
        XMLResource resource = (XMLResource) resourceSet.createResource(uri(workspaceId, id));
        ModelJsonMapper.fromJson(document.roots(), resource, ePackage,
                (modelId, elementId) -> resolveExternal(resourceSet, workspaceId, modelId, elementId));
        String name = document.name() == null || document.name().isBlank() ? meta.name() : document.name().strip();
        String role = document.role() == null ? meta.role() : blankToNull(document.role());
        ModelMeta next = meta.nextVersion(name, role);
        write(workspaceId, id, resource, next, ModelVersion.SAVED, null);
        return header(id, next).withRoots(ModelJsonMapper.toJson(resource));
    }

    /** Saves a resource modified in memory by a quick fix and bumps the version. */
    public synchronized ModelDocument save(String workspaceId, String id, XMLResource resource) {
        ModelMeta meta = readMeta(workspaceId, id);
        ModelMeta next = meta.nextVersion(meta.name(), meta.role());
        write(workspaceId, id, resource, next, ModelVersion.FIXED, null);
        return header(id, next).withRoots(ModelJsonMapper.toJson(resource));
    }

    public synchronized ModelDocument importXmi(String workspaceId, String fileName, byte[] content, String name,
            String role) {
        return importXmi(workspaceId, fileName, content, name, role, null);
    }

    public synchronized ModelDocument importXmi(String workspaceId, String fileName, byte[] content, String name,
            String role, String folder) {
        String base = fileName == null ? "" : Path.of(fileName).getFileName().toString();
        if (base.toLowerCase(Locale.ROOT).endsWith(".xmi")) {
            base = base.substring(0, base.length() - 4);
        }
        String id = base.replaceAll("[^A-Za-z0-9._-]", "-").replaceAll("^[^A-Za-z0-9]+", "");
        if (id.isEmpty()) {
            throw ApiException.badRequest("The imported file needs a name");
        }
        if (Files.exists(xmiFile(workspaceId, id))) {
            throw ApiException.conflict("A model with id '" + id + "' already exists");
        }
        XMLResource resource = (XMLResource) newResourceSet().createResource(uri(workspaceId, id));
        try {
            resource.load(new ByteArrayInputStream(content), XMI_OPTIONS);
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("The file is not a valid XMI model: " + rootMessage(e));
        }
        if (resource.getContents().isEmpty()) {
            throw ApiException.badRequest("The XMI file contains no element");
        }
        String nsUri = resource.getContents().get(0).eClass().getEPackage().getNsURI();
        EPackage ePackage = requirePackage(nsUri);
        String modelName = name == null || name.isBlank() ? id : name.strip();
        ModelMeta meta = new ModelMeta(modelName, ePackage.getNsURI(), 1, blankToNull(role), folderPath(folder));
        write(workspaceId, id, resource, meta, ModelVersion.IMPORTED, null);
        return header(id, meta).withRoots(ModelJsonMapper.toJson(resource));
    }

    // ---------------------------------------------------------------- sessions

    /**
     * A resource set in which several models of a workspace can be loaded and new ones created side by
     * side, so that the references between them are saved as relative hrefs.
     */
    public ResourceSet newSession() {
        return newResourceSet();
    }

    public synchronized XMLResource loadIn(ResourceSet session, String workspaceId, String id) {
        requireExists(workspaceId, id);
        return (XMLResource) session.getResource(uri(workspaceId, id), true);
    }

    /** Creates an empty, not yet saved resource for the model {@code id}. */
    public synchronized XMLResource createIn(ResourceSet session, String workspaceId, String id) {
        return (XMLResource) session.createResource(uri(workspaceId, id));
    }

    public synchronized boolean exists(String workspaceId, String id) {
        return Files.exists(metaFile(workspaceId, id)) && Files.exists(xmiFile(workspaceId, id));
    }

    /**
     * Saves a resource built by a derivation as the model {@code id}, replacing a previous one if present.
     * A new model goes in the given folder; one that is replaced stays where the user has put it.
     */
    public synchronized ModelDocument put(String workspaceId, String id, XMLResource resource, String name,
            String metamodel, String role, String folder) {
        EPackage ePackage = requirePackage(metamodel);
        ModelMeta previous = exists(workspaceId, id) ? readMeta(workspaceId, id) : null;
        ModelMeta meta = new ModelMeta(name, ePackage.getNsURI(), previous == null ? 1 : previous.version() + 1,
                blankToNull(role), previous == null ? folderPath(folder) : previous.folder());
        write(workspaceId, id, resource, meta, ModelVersion.DERIVED, null);
        return header(id, meta).withRoots(ModelJsonMapper.toJson(resource));
    }

    /** Moves a model to another folder of the workspace, or out of every folder with a blank one. */
    public synchronized ModelDocument move(String workspaceId, String id, String folder) {
        ModelMeta next = readMeta(workspaceId, id).inFolder(folderPath(folder));
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(metaFile(workspaceId, id).toFile(), next);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return header(id, next);
    }

    public synchronized void delete(String workspaceId, String id) {
        requireExists(workspaceId, id);
        Path dir = dir(workspaceId);
        try {
            Files.deleteIfExists(dir.resolve(id + ".xmi"));
            Files.deleteIfExists(dir.resolve(id + ".meta.json"));
            Files.deleteIfExists(dir.resolve(id + ".layout.json"));
            deleteHistory(workspaceId, id);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized JsonNode saveLayout(String workspaceId, String id, JsonNode layout) {
        requireExists(workspaceId, id);
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(dir(workspaceId).resolve(id + ".layout.json").toFile(),
                    layout);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return layout;
    }

    // ---------------------------------------------------------------- history

    /** The saved versions of a model, most recent first; the first one is the current content. */
    public synchronized List<ModelVersion> versions(String workspaceId, String id) {
        ModelMeta meta = readMeta(workspaceId, id);
        adoptCurrent(workspaceId, id, meta);
        List<ModelVersion> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(historyDir(workspaceId, id))) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).toList()) {
                ModelVersion version = json.readValue(file.toFile(), ModelVersion.class);
                if (version.version() <= meta.version()) {
                    result.add(version);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        result.sort(Comparator.comparingInt(ModelVersion::version).reversed());
        return result;
    }

    /** The content a model had at a past version, with references resolved against today's workspace. */
    public synchronized ModelDocument version(String workspaceId, String id, int version) {
        ModelMeta meta = readMeta(workspaceId, id);
        adoptCurrent(workspaceId, id, meta);
        ModelVersion info = readVersion(workspaceId, id, version);
        XMLResource resource = loadVersion(workspaceId, id, version);
        return new ModelDocument(id, info.name(), meta.metamodel(), info.role(), meta.folder(), version, null)
                .withRoots(ModelJsonMapper.toJson(resource));
    }

    /**
     * Brings back the content of a past version as a new version: nothing is lost, the history only
     * grows. Name and role stay the current ones.
     */
    public synchronized ModelDocument restore(String workspaceId, String id, int version) {
        ModelMeta meta = readMeta(workspaceId, id);
        adoptCurrent(workspaceId, id, meta);
        if (version == meta.version()) {
            throw ApiException.badRequest("Version " + version + " of '" + id + "' is already the current one");
        }
        readVersion(workspaceId, id, version);
        XMLResource resource = loadVersion(workspaceId, id, version);
        ModelMeta next = meta.nextVersion(meta.name(), meta.role());
        write(workspaceId, id, resource, next, ModelVersion.RESTORED, version);
        return header(id, next).withRoots(ModelJsonMapper.toJson(resource));
    }

    private XMLResource loadVersion(String workspaceId, String id, int version) {
        // Loaded under the address of the model itself, so that relative hrefs still find the other models.
        XMLResource resource = (XMLResource) newResourceSet().createResource(uri(workspaceId, id));
        try {
            resource.load(new ByteArrayInputStream(Files.readAllBytes(versionFile(workspaceId, id, version, ".xmi"))),
                    XMI_OPTIONS);
        } catch (IOException | RuntimeException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Version " + version + " of '" + id + "' cannot be read: " + rootMessage(e));
        }
        return resource;
    }

    private ModelVersion readVersion(String workspaceId, String id, int version) {
        Path file = versionFile(workspaceId, id, version, ".json");
        if (version < 1 || !Files.exists(file) || !Files.exists(versionFile(workspaceId, id, version, ".xmi"))) {
            throw ApiException.notFound("Model '" + id + "' has no version " + version + " in its history");
        }
        try {
            return json.readValue(file.toFile(), ModelVersion.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Models saved before the history existed, or edited on disk, get their current content recorded. */
    private void adoptCurrent(String workspaceId, String id, ModelMeta meta) {
        Path xmi = xmiFile(workspaceId, id);
        Path copy = versionFile(workspaceId, id, meta.version(), ".xmi");
        try {
            byte[] current = Files.readAllBytes(xmi);
            if (Files.exists(copy) && java.util.Arrays.equals(current, Files.readAllBytes(copy))) {
                return;
            }
            record(workspaceId, id, current, new ModelVersion(meta.version(), meta.name(), meta.role(),
                    Files.getLastModifiedTime(xmi).toInstant().truncatedTo(ChronoUnit.SECONDS).toString(), null,
                    null));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void record(String workspaceId, String id, byte[] xmi, ModelVersion version) throws IOException {
        Path dir = historyDir(workspaceId, id);
        Files.createDirectories(dir);
        Files.write(versionFile(workspaceId, id, version.version(), ".xmi"), xmi);
        json.writerWithDefaultPrettyPrinter().writeValue(versionFile(workspaceId, id, version.version(), ".json").toFile(),
                version);
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                String number = name.substring(0, Math.max(0, name.lastIndexOf('.')));
                if (number.matches("\\d{1,9}") && Integer.parseInt(number) <= version.version() - historySize) {
                    Files.deleteIfExists(file);
                }
            }
        }
    }

    private void deleteHistory(String workspaceId, String id) throws IOException {
        Path dir = historyDir(workspaceId, id);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(dir);
    }

    private Path historyDir(String workspaceId, String id) {
        return dir(workspaceId).resolve(HISTORY_DIR).resolve(checkId(id));
    }

    private Path versionFile(String workspaceId, String id, int version, String extension) {
        return historyDir(workspaceId, id).resolve(version + extension);
    }

    // ---------------------------------------------------------------- internals

    private ResourceSet newResourceSet() {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("xmi",
                (Resource.Factory) IdentifiedXmiResource::new);
        registry.registerIn(resourceSet);
        return resourceSet;
    }

    private EObject resolveExternal(ResourceSet resourceSet, String workspaceId, String modelId, String elementId) {
        if (modelId == null || elementId == null || !SAFE_NAME.matcher(modelId).matches()
                || !Files.exists(xmiFile(workspaceId, modelId))) {
            return null;
        }
        try {
            Resource resource = resourceSet.getResource(uri(workspaceId, modelId), true);
            return resource.getEObject(elementId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void write(String workspaceId, String id, XMLResource resource, ModelMeta meta, String origin,
            Integer restoredFrom) {
        try {
            Files.createDirectories(dir(workspaceId));
            if (meta.version() == 1) {
                // A new model must not inherit the history of a deleted one with the same id.
                deleteHistory(workspaceId, id);
            } else if (Files.exists(xmiFile(workspaceId, id)) && Files.exists(metaFile(workspaceId, id))) {
                adoptCurrent(workspaceId, id, json.readValue(metaFile(workspaceId, id).toFile(), ModelMeta.class));
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            resource.save(buffer, XMI_OPTIONS);
            Files.write(xmiFile(workspaceId, id), buffer.toByteArray());
            json.writerWithDefaultPrettyPrinter().writeValue(metaFile(workspaceId, id).toFile(), meta);
            record(workspaceId, id, buffer.toByteArray(), new ModelVersion(meta.version(), meta.name(), meta.role(),
                    Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), origin, restoredFrom));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ModelMeta readMeta(String workspaceId, String id) {
        requireExists(workspaceId, id);
        try {
            return json.readValue(metaFile(workspaceId, id).toFile(), ModelMeta.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void requireExists(String workspaceId, String id) {
        if (!Files.exists(metaFile(workspaceId, id)) || !Files.exists(xmiFile(workspaceId, id))) {
            throw ApiException.notFound("Unknown model: " + id);
        }
    }

    private EPackage requirePackage(String nsUri) {
        return registry.byNsUri(nsUri)
                .orElseThrow(() -> ApiException.badRequest("Unknown metamodel: " + nsUri));
    }

    /**
     * A folder as it is kept: levels separated by a single {@code /}, each one trimmed; null for no
     * folder. It is only a label, never a path on the disk.
     */
    static String folderPath(String folder) {
        if (folder == null) {
            return null;
        }
        List<String> levels = new ArrayList<>();
        for (String level : folder.split("/")) {
            String name = level.strip();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_FOLDER_NAME || name.chars().anyMatch(Character::isISOControl)) {
                throw ApiException.badRequest("Invalid folder name: " + name);
            }
            levels.add(name);
        }
        if (levels.size() > MAX_FOLDER_DEPTH) {
            throw ApiException.badRequest("Folders can be nested " + MAX_FOLDER_DEPTH + " levels at most");
        }
        return levels.isEmpty() ? null : String.join("/", levels);
    }

    private static ModelDocument header(String id, ModelMeta meta) {
        return new ModelDocument(id, meta.name(), meta.metamodel(), meta.role(), meta.folder(), meta.version(), null);
    }

    private Path dir(String workspaceId) {
        if (workspaceId == null || !SAFE_NAME.matcher(workspaceId).matches()) {
            throw ApiException.badRequest("Invalid workspace id");
        }
        return root.resolve(workspaceId);
    }

    private Path xmiFile(String workspaceId, String id) {
        return dir(workspaceId).resolve(checkId(id) + ".xmi");
    }

    private Path metaFile(String workspaceId, String id) {
        return dir(workspaceId).resolve(checkId(id) + ".meta.json");
    }

    private static String checkId(String id) {
        if (id == null || !SAFE_NAME.matcher(id).matches()) {
            throw ApiException.badRequest("Invalid model id");
        }
        return id;
    }

    private URI uri(String workspaceId, String id) {
        return URI.createFileURI(xmiFile(workspaceId, id).toString());
    }

    private String uniqueId(String workspaceId, String base) {
        String candidate = base;
        for (int i = 2; Files.exists(xmiFile(workspaceId, candidate)); i++) {
            candidate = base + "-" + i;
        }
        return candidate;
    }

    private static String slug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.isEmpty() ? "model" : slug;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /** XMI resource that gives every element a stable {@code xmi:id}. */
    private static final class IdentifiedXmiResource extends XMIResourceImpl {

        IdentifiedXmiResource(URI uri) {
            super(uri);
            setEncoding("UTF-8");
        }

        @Override
        protected boolean useUUIDs() {
            return true;
        }
    }
}
