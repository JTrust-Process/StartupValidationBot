package com.startupvalidationbot.diligence.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RobotsRulesTest {
    @Test void wildcardsMatchOnlyTheirPaths() {
        String rules = "User-agent: *\nDisallow: /offering/*/invest$";
        assertThat(RobotsRules.disallowed(rules, "/offering/acme/invest")).isTrue();
        assertThat(RobotsRules.disallowed(rules, "/offering/acme")).isFalse();
        assertThat(RobotsRules.disallowed(rules, "/explore")).isFalse();
    }
    @Test void longestRuleAndAllowTieTakePrecedence() {
        String rules = "User-agent: *\nDisallow: /\nAllow: /offering/\nDisallow: /offering/acme/private\nAllow: /offering/acme/private";
        assertThat(RobotsRules.disallowed(rules, "/offering/acme")).isFalse();
        assertThat(RobotsRules.disallowed(rules, "/other")).isTrue();
        assertThat(RobotsRules.disallowed(rules, "/offering/acme/private")).isFalse();
    }
    @Test void namedGroupsOverrideWildcardAndAreCombined() {
        String rules = "User-agent: *\nDisallow: /\nUser-agent: StartupIntelligence\nDisallow: /private\nUser-agent: startupintelligence\nDisallow: /secret";
        assertThat(RobotsRules.disallowed(rules, "/campaign")).isFalse();
        assertThat(RobotsRules.disallowed(rules, "/private")).isTrue();
        assertThat(RobotsRules.disallowed(rules, "/secret")).isTrue();
    }
    @Test void commentsEmptyRulesUnreservedEscapesAndCaseAreHandled() {
        String rules = "User-agent: *\nDisallow:\nDisallow: /acme # comment\nDisallow: /SECRET";
        assertThat(RobotsRules.disallowed(rules, "/%61cme")).isTrue();
        assertThat(RobotsRules.disallowed(rules, "/secret")).isFalse();
        assertThat(RobotsRules.disallowed(rules, "/other")).isFalse();
    }
}
