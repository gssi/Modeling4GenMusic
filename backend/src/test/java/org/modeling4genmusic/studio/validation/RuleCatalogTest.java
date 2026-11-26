package org.modeling4genmusic.studio.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.modeling4genmusic.studio.epsilon.Scripts;

class RuleCatalogTest {

    private static final ValidationProfile INTENT = new ValidationProfile("http://music/exercise",
            List.of("derived"), "intent.evl", "T", null, null);

    @Test
    void describesTheRulesAsTheParserReadsThem() {
        RuleSet set = RuleCatalog.describe(INTENT, Scripts.read("intent.evl"));
        assertThat(set.problems()).isEmpty();
        assertThat(set.exceptRoles()).containsExactly("derived");
        assertThat(set.contexts()).extracting(RuleSet.Context::type).containsExactly("T!ExerciseSpec",
                "T!ContainsPattern", "T!ContainsPattern", "T!QualityGoal");

        RuleSet.Rule tempo = set.contexts().get(0).rules().get(0);
        assertThat(tempo.name()).isEqualTo("TempoAboveComfort");
        assertThat(tempo.severity()).isEqualTo(Diagnostic.Severity.WARNING);
        assertThat(tempo.guard()).isEqualTo("self.hasLearner()");
        assertThat(tempo.check()).startsWith("self.requestedBpm <= self.learner.comfortableBpm").contains("\nor (");
        assertThat(tempo.message()).startsWith("\"Il tempo richiesto (\"");
        assertThat(tempo.fixes()).hasSize(1);
        assertThat(tempo.fixes().get(0).title()).isEqualTo("\"Limita il tempo a \" + self.learner.comfortableBpm + \" bpm\"");
        assertThat(tempo.fixes().get(0).body()).isEqualTo("self.maxBpm = self.learner.comfortableBpm;");
        assertThat(tempo.source()).startsWith("critique TempoAboveComfort {").endsWith("}");
        assertThat(tempo.line()).isEqualTo(6);

        RuleSet.Rule conflict = set.contexts().get(1).rules().get(0);
        assertThat(conflict.name()).isEqualTo("ContainsExcludesConflict");
        assertThat(conflict.severity()).isEqualTo(Diagnostic.Severity.ERROR);
        assertThat(conflict.fixes()).isEmpty();

        RuleSet.Rule leap = set.contexts().get(2).rules().get(0);
        assertThat(leap.name()).isEqualTo("LeapBeyondReach");
        assertThat(leap.comment()).startsWith("A leap wider than the hand opens can be played");

        assertThat(set.operations()).extracting(RuleSet.Operation::name).contains("hasLearner", "reach");
        assertThat(set.operations()).filteredOn(o -> o.name().equals("reach")).first()
                .satisfies(o -> assertThat(o.context()).isEqualTo("L!Learner"));
    }

    @Test
    void reportsSyntaxErrorsInsteadOfRules() {
        RuleSet set = RuleCatalog.describe(INTENT, "context T!ExerciseSpec {\n  constraint Broken {\n    check self.bars > 0\n  }\n}\n");
        assertThat(set.problems()).isNotEmpty();
        assertThat(set.problems().get(0).line()).isEqualTo(3);
        assertThat(set.contexts()).isEmpty();
    }
}
