package org.modeling4genmusic.studio.metamodel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.modeling4genmusic.studio.config.StudioProperties;
import org.modeling4genmusic.studio.metamodel.MetamodelDescriptor.AttributeDescriptor;
import org.modeling4genmusic.studio.metamodel.MetamodelDescriptor.ClassDescriptor;
import org.modeling4genmusic.studio.metamodel.MetamodelDescriptor.EnumDescriptor;
import org.modeling4genmusic.studio.metamodel.MetamodelDescriptor.ReferenceDescriptor;
import org.modeling4genmusic.studio.metamodel.MetamodelDescriptor.TypeRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Loads every {@code .ecore} file of the metamodels folder and keeps the resulting packages by nsURI.
 * All files are loaded in one resource set so that the relative hrefs between them resolve.
 */
@Component
public class MetamodelRegistry {

    private final Map<String, EPackage> byNsUri = new LinkedHashMap<>();

    @Autowired
    public MetamodelRegistry(StudioProperties properties) {
        this(properties.metamodelsPath());
    }

    public MetamodelRegistry(Path metamodelsDir) {
        ResourceSet resourceSet = new ResourceSetImpl();
        resourceSet.getResourceFactoryRegistry().getExtensionToFactoryMap().put("ecore",
                new EcoreResourceFactoryImpl());
        List<Path> files;
        try (Stream<Path> stream = Files.list(metamodelsDir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".ecore")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read metamodels folder " + metamodelsDir.toAbsolutePath(), e);
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("No .ecore file found in " + metamodelsDir.toAbsolutePath());
        }
        for (Path file : files) {
            resourceSet.getResource(URI.createFileURI(file.toAbsolutePath().toString()), true);
        }
        EcoreUtil.resolveAll(resourceSet);
        for (Resource resource : resourceSet.getResources()) {
            for (var content : resource.getContents()) {
                if (content instanceof EPackage ePackage) {
                    byNsUri.put(ePackage.getNsURI(), ePackage);
                }
            }
        }
    }

    public Collection<EPackage> packages() {
        return byNsUri.values();
    }

    public Optional<EPackage> byNsUri(String nsUri) {
        return Optional.ofNullable(byNsUri.get(nsUri));
    }

    public Optional<EPackage> byPrefix(String prefix) {
        return byNsUri.values().stream().filter(p -> p.getNsPrefix().equals(prefix)).findFirst();
    }

    /** Registers the known packages in a resource set that is going to load or save models. */
    public void registerIn(ResourceSet resourceSet) {
        byNsUri.forEach((nsUri, ePackage) -> resourceSet.getPackageRegistry().put(nsUri, ePackage));
    }

    public List<MetamodelDescriptor.Summary> summaries() {
        return byNsUri.values().stream()
                .map(p -> new MetamodelDescriptor.Summary(p.getNsURI(), p.getNsPrefix(), p.getName())).toList();
    }

    public MetamodelDescriptor describe(EPackage ePackage) {
        List<ClassDescriptor> classes = new ArrayList<>();
        List<EnumDescriptor> enums = new ArrayList<>();
        for (EClassifier classifier : ePackage.getEClassifiers()) {
            if (classifier instanceof EClass eClass) {
                classes.add(describe(eClass));
            } else if (classifier instanceof EEnum eEnum) {
                enums.add(new EnumDescriptor(eEnum.getName(), literals(eEnum)));
            }
        }
        return new MetamodelDescriptor(ePackage.getNsURI(), ePackage.getNsPrefix(), ePackage.getName(), classes,
                enums);
    }

    private ClassDescriptor describe(EClass eClass) {
        List<AttributeDescriptor> attributes = new ArrayList<>();
        for (EAttribute attribute : eClass.getEAllAttributes()) {
            if (isEditable(attribute)) {
                attributes.add(describe(attribute));
            }
        }
        List<ReferenceDescriptor> references = new ArrayList<>();
        for (EReference reference : eClass.getEAllReferences()) {
            if (isEditable(reference)) {
                references.add(new ReferenceDescriptor(reference.getName(), typeRef(reference.getEReferenceType()),
                        reference.isContainment(), reference.getLowerBound(), reference.getUpperBound()));
            }
        }
        List<TypeRef> supertypes = eClass.getEAllSuperTypes().stream().map(MetamodelRegistry::typeRef).toList();
        return new ClassDescriptor(eClass.getName(), eClass.isAbstract() || eClass.isInterface(), supertypes,
                attributes, references);
    }

    private static AttributeDescriptor describe(EAttribute attribute) {
        EDataType dataType = attribute.getEAttributeType();
        Object defaultValue = attribute.getDefaultValue();
        if (dataType instanceof EEnum eEnum) {
            String literal = defaultValue instanceof EEnumLiteral l ? l.getName() : null;
            return new AttributeDescriptor(attribute.getName(), "enum", typeRef(eEnum), literals(eEnum),
                    attribute.getLowerBound(), attribute.getUpperBound(), literal);
        }
        return new AttributeDescriptor(attribute.getName(), primitiveName(dataType), null, null,
                attribute.getLowerBound(), attribute.getUpperBound(), defaultValue);
    }

    /** Features the editor can read and write: derived, transient and read-only ones are left out. */
    public static boolean isEditable(EStructuralFeature feature) {
        return feature.isChangeable() && !feature.isDerived() && !feature.isTransient();
    }

    public static String primitiveName(EDataType dataType) {
        Class<?> type = dataType.getInstanceClass();
        if (type == int.class || type == Integer.class || type == long.class || type == Long.class
                || type == short.class || type == Short.class) {
            return "int";
        }
        if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "double";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        return "string";
    }

    private static List<String> literals(EEnum eEnum) {
        return eEnum.getELiterals().stream().map(EEnumLiteral::getName).toList();
    }

    public static TypeRef typeRef(EClassifier classifier) {
        return new TypeRef(classifier.getEPackage().getNsURI(), classifier.getName());
    }
}
