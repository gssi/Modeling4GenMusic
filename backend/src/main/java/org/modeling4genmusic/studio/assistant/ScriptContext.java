package org.modeling4genmusic.studio.assistant;

import java.util.List;
import java.util.Map;

/**
 * What a language model needs to know about a script to add a rule to it.
 *
 * @param models     name of each model inside the script to the nsURI of its metamodel, in declaration order
 * @param readOnly   names of the models the script may only read
 * @param tools      helper objects the script can call: variable name to class name
 * @param operations first line and comment of the operations the script declares
 * @param examples   rules of the script, complete, to show the style
 * @param names      names already taken by rules of the script
 */
record ScriptContext(String script, RuleLanguage language, Map<String, String> models, List<String> readOnly,
        Map<String, String> tools, List<String> operations, List<String> examples, List<String> names) {
}
