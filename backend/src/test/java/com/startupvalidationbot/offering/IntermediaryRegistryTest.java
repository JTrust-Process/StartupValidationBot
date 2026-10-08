package com.startupvalidationbot.offering;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IntermediaryRegistryTest {
    @ParameterizedTest @CsvSource({"0001670254,Wefunder", "0001751525,Republic", "0001725012,StartEngine", "0001665160,StartEngine", "0001872856,DealMaker", "0001788777,Fundify", "0001935609,GigaStar"})
    void exactCikIsIndependentOfPlatformFetch(String cik, String family) {
        var identity = IntermediaryRegistry.resolve(null, cik, null, null, null);
        assertThat(identity.family()).isEqualTo(family);
        assertThat(identity.state()).isEqualTo(IntermediaryRegistry.State.SEC_CONFIRMED);
    }
    @Test void namesCrdAndFileNumbersAreExactNotFuzzy() {
        assertThat(IntermediaryRegistry.resolve("Wefunder Portal, LLC.", null, null, null, null).family()).isEqualTo("Wefunder");
        assertThat(IntermediaryRegistry.resolve(null, null, "000315324", null, null).family()).isEqualTo("DealMaker");
        assertThat(IntermediaryRegistry.resolve(null, null, null, "008-70060", null).family()).isEqualTo("StartEngine");
        assertThat(IntermediaryRegistry.resolve("Wefunder-like unrelated portal", null, null, null, null).state()).isEqualTo(IntermediaryRegistry.State.UNKNOWN);
    }
    @Test void knownConflictingIdentifiersAreAmbiguousAndUnknownIsNeverGuessed() {
        var conflict = IntermediaryRegistry.resolve("OpenDeal Portal LLC", "0001670254", null, null, null);
        assertThat(conflict.family()).isNull(); assertThat(conflict.state()).isEqualTo(IntermediaryRegistry.State.AMBIGUOUS);
        assertThat(IntermediaryRegistry.resolve("Unregistered Portal LLC", "1234", null, null, null).family()).isNull();
        assertThat(IntermediaryRegistry.resolve(null, null, null, null, "https://wefunder.com/").state()).isEqualTo(IntermediaryRegistry.State.EXPLICIT_DOMAIN_CONFIRMED);
        assertThat(IntermediaryRegistry.resolve(null, null, null, null, "https://wefunder.com.attacker.example/").family()).isNull();
    }
}
