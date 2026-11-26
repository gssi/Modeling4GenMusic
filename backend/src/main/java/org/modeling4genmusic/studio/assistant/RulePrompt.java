package org.modeling4genmusic.studio.assistant;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnum;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;
import org.modeling4genmusic.studio.generation.OllamaClient.Message;
import org.modeling4genmusic.studio.metamodel.MetamodelRegistry;

/**
 * Builds the conversation that asks a language model for a rule. Everything specific to the domain is
 * read from the metamodels, the descriptors and the script itself: nothing is written here by hand.
 */
final class RulePrompt {

    private static final String EVL_PRIMER = """
            You write rules in the Epsilon Validation Language (EVL), whose expressions are in the Epsilon \
            Object Language (EOL). Reminders:
            - A rule lives in a context: `context M!Class { ... }`, where M is the name of a model.
            - `constraint Name { ... }` reports an error, `critique Name { ... }` reports a warning.
            - Parts of a rule, in this order: optional `guard: <boolean>`, `check: <boolean>` (true when the \
            element is fine), `message: <string>`. `self` is the element being checked.
            - EOL: `and`, `or`, `not`; equality is `=`, inequality `<>`; strings are joined with `+`; \
            `x.isDefined()`; collections have `size()`, `isEmpty()`, `exists(e | ...)`, `forAll(e | ...)`, \
            `select(e | ...)`, `collect(e | ...)`; `x.isTypeOf(M!Class)`, `x.isKindOf(M!Class)`; \
            `self.eContainer()` is the containing element; an enumeration literal is `M!Enum#LITERAL`; \
            all the elements of a class are `M!Class.all`.
            - `check` must be true exactly when the element is acceptable; the message describes what is \
            wrong when it is false. Use a tool or an operation only for what its name says.
            - There is no `if` expression, no `==`, no `&&`, no `!`, no `null` checks with `!=`.""";

    private static final String ETL_PRIMER = """
            You write rules in the Epsilon Transformation Language (ETL), whose statements are in the Epsilon \
            Object Language (EOL). Reminders:
            - `rule Name transform s : IN!Class to t : OUT!Class { guard: <boolean>  <statements> }` creates \
            one `t` for every `s` that passes the guard; the body sets the features of `t`.
            - `post Name { <statements> }` runs after all the rules and can change what they created.
            - EOL statements end with `;`: `var x = ...;`, `t.name = s.name;`, `c.items.add(x);`, \
            `if (cond) { ... } else { ... }`, `for (e in collection) { ... }`, `new OUT!Class`.
            - EOL expressions: `and`, `or`, `not`; equality is `=`, inequality `<>`; strings are joined with \
            `+`; `x.isDefined()`; collections have `size()`, `isEmpty()`, `exists(e | ...)`, \
            `select(e | ...)`, `first()`; `x.isTypeOf(M!Class)`; an enumeration literal is `M!Enum#LITERAL`; \
            `s.equivalent()` is the element a rule created from `s`.
            - `new M!Class` takes no initialiser block: create the element, then set its features one \
            statement at a time.
            - Elements are created only in the models the script writes: the type after `to` and after \
            `new` always uses the name of a written model, never of a read-only one.
            - There is no `==`, no `&&`, no `!`.""";

    private RulePrompt() {
    }

    /**
     * @param description what the rule must do, in the words of the user
     * @param previous    earlier answers with the errors each one caused, oldest first
     */
    static List<Message> build(ScriptContext context, MetamodelRegistry registry, String description,
            List<Attempt> previous) {
        boolean evl = context.language() == RuleLanguage.EVL;
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", (evl ? EVL_PRIMER : ETL_PRIMER) + "\n\n" + """
                You add one rule to an existing script. Answer with the code of the new rule only, in one \
                fenced code block, with no explanation. Do not repeat or change the rules already in the \
                script. Use only the classes, features, operations and tools listed by the user: never \
                invent a feature. Rule names are in UpperCamelCase English and must be new. Texts shown \
                to people (messages, rationales) are in Italian. Never use `Native`."""));

        StringBuilder user = new StringBuilder();
        user.append("Script: ").append(context.script()).append("\n\n");
        user.append("# Models and their classes\n\n").append(models(context, registry)).append("\n");
        if (!context.tools().isEmpty()) {
            user.append("# Tools (global variables)\n\n").append(tools(context.tools())).append("\n");
        }
        if (!context.operations().isEmpty()) {
            user.append("# Operations already declared in the script\n\n");
            context.operations().forEach(op -> user.append(op).append("\n"));
            user.append("\n");
        }
        if (!context.examples().isEmpty()) {
            user.append("# Rules of the script, as examples of style\n\n```\n")
                    .append(String.join("\n\n", context.examples())).append("\n```\n\n");
        }
        if (!context.names().isEmpty()) {
            user.append("Names already taken: ").append(String.join(", ", context.names())).append("\n\n");
        }
        user.append("# The rule to write\n\n").append(description.strip()).append("\n");
        messages.add(new Message("user", user.toString()));

        for (Attempt attempt : previous) {
            messages.add(new Message("assistant", attempt.answer()));
            messages.add(new Message("user", "That answer cannot be used:\n"
                    + attempt.errors().stream().map(e -> "- " + e).collect(Collectors.joining("\n"))
                    + "\nAnswer again with the corrected rule only, in one fenced code block."));
        }
        return messages;
    }

    /** An answer of the model and what was wrong with it. */
    record Attempt(String answer, List<String> errors) {
    }

    /** Classes and enumerations of each metamodel, under the names of the models that conform to it. */
    static String models(ScriptContext context, MetamodelRegistry registry) {
        Map<String, List<String>> aliasesByMetamodel = new LinkedHashMap<>();
        context.models().forEach((alias, nsUri) -> aliasesByMetamodel.computeIfAbsent(nsUri, k -> new ArrayList<>())
                .add(alias));
        Map<String, String> firstAlias = new LinkedHashMap<>();
        aliasesByMetamodel.forEach((nsUri, aliases) -> firstAlias.put(nsUri, aliases.get(0)));

        StringBuilder text = new StringBuilder();
        aliasesByMetamodel.forEach((nsUri, aliases) -> {
            EPackage ePackage = registry.byNsUri(nsUri).orElse(null);
            if (ePackage == null) {
                return;
            }
            text.append(aliases.size() == 1 ? "Model " : "Models ").append(aliases.stream()
                    .map(a -> a + (context.readOnly().contains(a) ? " (read only)" : " (written by the script)"))
                    .collect(Collectors.joining(", ")));
            text.append(": write ").append(aliases.stream().map(a -> a + "!Class").collect(Collectors.joining(" or ")))
                    .append(".\n");
            String alias = aliases.get(0);
            for (EClassifier classifier : ePackage.getEClassifiers()) {
                if (classifier instanceof EEnum eEnum) {
                    text.append("  enum ").append(alias).append('!').append(eEnum.getName()).append(" { ")
                            .append(eEnum.getELiterals().stream().map(l -> l.getName())
                                    .collect(Collectors.joining(", ")))
                            .append(" }\n");
                } else if (classifier instanceof EClass eClass) {
                    text.append("  ").append(eClass.isAbstract() ? "abstract class " : "class ").append(alias)
                            .append('!').append(eClass.getName());
                    if (!eClass.getESuperTypes().isEmpty()) {
                        text.append(" extends ").append(eClass.getESuperTypes().stream().map(EClass::getName)
                                .collect(Collectors.joining(", ")));
                    }
                    List<String> features = new ArrayList<>();
                    for (EAttribute attribute : eClass.getEAttributes()) {
                        features.add(attribute.getName() + " : " + attribute.getEAttributeType().getName()
                                + (attribute.isMany() ? "[*]" : ""));
                    }
                    for (EReference reference : eClass.getEReferences()) {
                        EClass target = reference.getEReferenceType();
                        String owner = firstAlias.getOrDefault(target.getEPackage().getNsURI(), alias);
                        features.add(reference.getName() + " : " + owner + "!" + target.getName()
                                + (reference.isMany() ? "[*]" : "") + (reference.isContainment() ? " (contained)" : ""));
                    }
                    text.append(features.isEmpty() ? " { }" : " { " + String.join("; ", features) + " }").append("\n");
                }
            }
        });
        return text.toString();
    }

    /** Public methods of each tool, read from its class. */
    static String tools(Map<String, String> tools) {
        StringBuilder text = new StringBuilder();
        tools.forEach((name, className) -> {
            try {
                Class<?> type = Class.forName(className, false, RulePrompt.class.getClassLoader());
                List<String> inherited = Arrays.stream(type.getInterfaces())
                        .flatMap(i -> Arrays.stream(i.getMethods())).map(Method::getName).toList();
                text.append(name).append(" is a ").append(type.getSimpleName()).append(":\n");
                Arrays.stream(type.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()))
                        .filter(m -> !inherited.contains(m.getName())).sorted(Comparator.comparing(Method::getName)).forEach(m -> text.append("  ").append(name).append('.')
                                .append(m.getName()).append('(')
                                .append(Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName)
                                        .collect(Collectors.joining(", ")))
                                .append(") : ").append(m.getReturnType().getSimpleName()).append("\n"));
            } catch (ClassNotFoundException | LinkageError e) {
                text.append(name).append(" (methods not known)\n");
            }
        });
        return text.toString();
    }
}
