package org.modeling4genmusic.studio.needs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;

/** The reflective steps of the interpretation of needs, on a small catalog built in memory. */
class NeedToolTest {

    private static final String NEEDS = "http://music/needs";
    private static final String PROFILE = "http://music/profile";
    private static final String EXERCISE = "http://music/exercise";

    private final NeedTool tool = new NeedTool();
    private MetamodelRegistry registry;
    private Resource resource;

    private EObject stretch;
    private EObject hand;
    private EObject maxBars;
    private EObject spanEffect;
    private EObject barsEffect;
    private EObject type;
    private EObject need;
    private EObject learner;

    @BeforeEach
    void setUp() {
        registry = new MetamodelRegistry(Path.of("../metamodels"));
        ResourceSet resourceSet = new ResourceSetImpl();
        registry.registerIn(resourceSet);
        resource = new XMIResourceImpl(URI.createURI("memory:test"));
        resourceSet.getResources().add(resource);

        hand = parameter("hand", "HAND", null, null, "RIGHT");
        stretch = parameter("maxStretch", "INTERVAL", 2.0, 24.0, "12");
        maxBars = parameter("maxBars", "INTEGER", 1.0, 64.0, null);

        spanEffect = create(NEEDS, "ConstraintEffect", Map.of("constraintClass", "SpanConstraint"));
        add(spanEffect, "bindings", binding("hand", hand, null));
        add(spanEffect, "bindings", binding("maxStretchInPosition", stretch, null));
        barsEffect = create(NEEDS, "SpecEffect",
                Map.of("feature", "bars", "rationale", "At most {maxBars} bars for the {hand} hand"));
        barsEffect.eSet(feature(barsEffect, "parameter"), maxBars);

        type = create(NEEDS, "NeedType", Map.of("name", "Limited"));
        for (EObject parameter : List.of(hand, stretch, maxBars)) {
            add(type, "parameters", parameter);
        }
        add(type, "effects", spanEffect);
        add(type, "effects", barsEffect);
        EObject catalog = create(NEEDS, "NeedCatalog", Map.of());
        add(catalog, "types", type);

        need = create(PROFILE, "Need", Map.of());
        need.eSet(feature(need, "type"), type);
        learner = create(PROFILE, "Learner", Map.of("name", "anna"));
        add(learner, "needs", need);
        resource.getContents().addAll(List.of(catalog, learner));
    }

    // ---------------------------------------------------------------- builders

    private EObject create(String nsUri, String className, Map<String, Object> attributes) {
        EClass eClass = (EClass) registry.byNsUri(nsUri).orElseThrow().getEClassifier(className);
        EObject element = EcoreUtil.create(eClass);
        attributes.forEach((name, value) -> {
            EStructuralFeature feature = feature(element, name);
            element.eSet(feature, feature.getEType() instanceof EEnum e ? e.getEEnumLiteral(value.toString()).getInstance()
                    : value);
        });
        return element;
    }

    private EObject parameter(String name, String kind, Double min, Double max, String defaultValue) {
        EObject parameter = create(NEEDS, "ParameterDef", Map.of("name", name, "kind", kind));
        parameter.eSet(feature(parameter, "min"), min);
        parameter.eSet(feature(parameter, "max"), max);
        parameter.eSet(feature(parameter, "defaultValue"), defaultValue);
        return parameter;
    }

    private EObject binding(String featureName, EObject parameter, String literal) {
        EObject binding = create(NEEDS, "Binding", Map.of("feature", featureName));
        binding.eSet(feature(binding, "parameter"), parameter);
        binding.eSet(feature(binding, "literal"), literal);
        return binding;
    }

    private EObject slot(EObject parameter, String value) {
        EObject slot = create(PROFILE, "Slot", Map.of("value", value));
        slot.eSet(feature(slot, "parameter"), parameter);
        add(need, "slots", slot);
        return slot;
    }

    private static EStructuralFeature feature(EObject element, String name) {
        return element.eClass().getEStructuralFeature(name);
    }

    @SuppressWarnings("unchecked")
    private static void add(EObject owner, String name, EObject child) {
        ((List<EObject>) owner.eGet(feature(owner, name))).add(child);
    }

    // ---------------------------------------------------------------- values of a need

    @Test
    void aParameterTakesTheSlotOrElseTheDefaultOfTheType() {
        assertThat(tool.parameterValue(need, stretch)).isEqualTo("12");
        assertThat(tool.slotOf(need, stretch)).isNull();
        assertThat(tool.parameterValue(need, maxBars)).isNull();

        EObject slot = slot(stretch, " 9 ");
        assertThat(tool.parameterValue(need, stretch)).isEqualTo("9");
        assertThat(tool.slotOf(need, stretch)).isSameAs(slot);
        // An empty slot does not hide the default.
        slot.eSet(feature(slot, "value"), "  ");
        assertThat(tool.parameterValue(need, stretch)).isEqualTo("12");
    }

    @Test
    void aValueIsItsParameterOrItsLiteral() {
        assertThat(tool.valueOf(need, binding("hand", hand, null))).isEqualTo("RIGHT");
        assertThat(tool.valueOf(need, binding("pattern.expression", null, "16 16"))).isEqualTo("16 16");
        assertThat(tool.valueOf(need, binding("hand", null, null))).isNull();
        assertThat(tool.valueOf(need, barsEffect)).isNull();
    }

    @Test
    void placeholdersOfARationaleAreFilled() {
        assertThat(tool.fill("At most {maxBars} bars", "maxBars", "4")).isEqualTo("At most 4 bars");
        assertThat(tool.fill("At most {maxBars} bars", "maxBars", null)).isEqualTo("At most ? bars");
        assertThat(tool.fill(null, "maxBars", "4")).isEmpty();
    }

    @Test
    void theConstraintsOfAClassAreReadFromTheTypesOfTheNeeds() {
        slot(stretch, "7");
        assertThat(tool.constraints(learner, "SpanConstraint"))
                .containsExactly(Map.of("hand", "RIGHT", "maxStretchInPosition", "7"));
        assertThat(tool.constraints(learner, "RangeConstraint")).isEmpty();
        assertThat(tool.constraints(null, "SpanConstraint")).isEmpty();

        // A need whose type cannot be found says nothing.
        need.eSet(feature(need, "type"), null);
        assertThat(tool.constraints(learner, "SpanConstraint")).isEmpty();
    }

    @Test
    void theLimitsOnTheSpecAreListedOnlyWhenTheyHaveAValue() {
        assertThat(tool.limits(learner)).isEmpty();
        slot(maxBars, "4");
        assertThat(tool.limits(learner)).containsExactly(Map.of("type", "Limited", "feature", "bars", "value", "4",
                "combine", "MIN", "rationale", "At most 4 bars for the RIGHT hand"));
        assertThat(tool.limits(null)).isEmpty();
    }

    @Test
    void aBoundIsExceededAccordingToHowItCombines() {
        assertThat(tool.exceeds("8", "4", "MIN")).isTrue();
        assertThat(tool.exceeds("4", "4", "MIN")).isFalse();
        assertThat(tool.exceeds("4", "4.0", "SET")).isFalse();
        assertThat(tool.exceeds("2", "4", "MAX")).isTrue();
        assertThat(tool.exceeds("6", "4", "MAX")).isFalse();
        assertThat(tool.exceeds("G", "C", "SET")).isTrue();
        assertThat(tool.exceeds(null, "4", "MIN")).isFalse();
    }

    // ---------------------------------------------------------------- writing the spec

    @Test
    void featuresAreSetFromTextAlsoThroughAContainedElement() {
        EObject excludes = create(EXERCISE, "ExcludesPattern", Map.of());
        List<EObject> created = tool.set(excludes, "pattern.kind", "INTERVAL");
        assertThat(created).hasSize(1);
        EObject pattern = created.get(0);
        assertThat(pattern.eClass().getName()).isEqualTo("Pattern");
        assertThat(pattern.eContainer()).isSameAs(excludes);
        // The second path reuses the element the first one created.
        assertThat(tool.set(excludes, "pattern.expression", "16 16")).isEmpty();
        assertThat(tool.read(pattern, "expression")).isEqualTo("16 16");
        assertThat(tool.read(pattern, "kind")).isEqualTo("INTERVAL");

        EObject span = create(EXERCISE, "SpanConstraint", Map.of());
        tool.set(span, "maxStretchInPosition", "9");
        assertThat(span.eGet(feature(span, "maxStretchInPosition"))).isEqualTo(9);

        assertThatThrownBy(() -> tool.set(span, "maxStretchInPosition", "wide"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.set(span, "missing", "9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.set(span, "hand.name", "9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aLimitIsACeilingAFloorOrAReplacement() {
        EObject spec = create(EXERCISE, "ExerciseSpec", Map.of());
        assertThat(tool.read(spec, "bars")).isNull();
        // Nothing to compare with: the value is taken.
        assertThat(tool.limit(spec, "bars", "8", "MIN")).isTrue();
        assertThat(tool.limit(spec, "bars", "12", "MIN")).isFalse();
        assertThat(tool.limit(spec, "bars", "8", "MIN")).isFalse();
        assertThat(tool.limit(spec, "bars", "4", "MIN")).isTrue();
        assertThat(tool.read(spec, "bars")).isEqualTo("4");
        assertThat(tool.limit(spec, "bars", "2", "MAX")).isFalse();
        assertThat(tool.limit(spec, "bars", "6", "MAX")).isTrue();
        assertThat(tool.limit(spec, "bars", "16", "SET")).isTrue();
        assertThat(tool.limit(spec, "bars", "16", "SET")).isFalse();
        assertThat(tool.read(spec, "bars")).isEqualTo("16");
    }

    // ---------------------------------------------------------------- checks for the scripts

    @Test
    void aValueIsCheckedAgainstTheKindAndTheBoundsOfItsParameter() {
        assertThat(tool.problemOf(stretch, "9")).isNull();
        assertThat(tool.problemOf(stretch, "")).isEqualTo("manca il valore");
        assertThat(tool.problemOf(stretch, "1")).contains("sotto il minimo ammesso (2)");
        assertThat(tool.problemOf(stretch, "25")).contains("sopra il massimo ammesso (24)");
        assertThat(tool.problemOf(stretch, "9.5")).contains("non è un numero intero");
        assertThat(tool.problemOf(stretch, "wide")).contains("non è un numero");
        assertThat(tool.problemOf(hand, "LEFT")).isNull();
        assertThat(tool.problemOf(hand, "MIDDLE")).contains("LEFT o RIGHT");
        EObject rhythm = parameter("figure", "RHYTHM", null, null, null);
        assertThat(tool.problemOf(rhythm, "8. 16")).isNull();
        assertThat(tool.problemOf(rhythm, "8 x")).isNotBlank();
    }

    @Test
    void theTargetOfAnEffectMustBeAConcreteClassOfTheRightKind() {
        assertThat(tool.classProblem(type, EXERCISE, "SpanConstraint", "Constraint")).isNull();
        assertThat(tool.classProblem(type, EXERCISE, "Nothing", "Constraint")).contains("non esiste");
        assertThat(tool.classProblem(type, EXERCISE, "Constraint", null)).contains("astratta");
        assertThat(tool.classProblem(type, EXERCISE, "Pattern", "Constraint")).contains("non è un Constraint");
    }

    @Test
    void aBindingMustFitTheFeatureItWrites() {
        assertThat(tool.bindingProblem(type, EXERCISE, "SpanConstraint", "hand", binding("hand", hand, null))).isNull();
        assertThat(tool.bindingProblem(type, EXERCISE, "SpanConstraint", "maxStretchInPosition",
                binding("maxStretchInPosition", hand, null))).contains("non accetta un parametro di tipo HAND");
        assertThat(tool.bindingProblem(type, EXERCISE, "SpanConstraint", "reach", binding("reach", stretch, null)))
                .contains("non è un attributo");
        assertThat(tool.bindingProblem(type, EXERCISE, "ExcludesPattern", "pattern.kind",
                binding("pattern.kind", null, "RHYTHM"))).isNull();
        assertThat(tool.bindingProblem(type, EXERCISE, "ExcludesPattern", "pattern.kind",
                binding("pattern.kind", null, "NOISE"))).contains("non è un valore");
        assertThat(tool.bindingProblem(type, EXERCISE, "ExcludesPattern", "pattern.kind",
                binding("pattern.kind", null, null))).contains("manca il valore");
        assertThat(tool.bindingProblem(type, EXERCISE, "ExcludesPattern", "weight.kind",
                binding("weight.kind", null, "RHYTHM"))).contains("non è un elemento contenuto singolo");

        assertThat(tool.isNumeric(type, EXERCISE, "ExerciseSpec", "bars")).isTrue();
        assertThat(tool.isNumeric(type, EXERCISE, "ExerciseSpec", "key")).isFalse();
        assertThat(tool.isNumeric(type, EXERCISE, "ExerciseSpec", "missing")).isFalse();
    }
}
