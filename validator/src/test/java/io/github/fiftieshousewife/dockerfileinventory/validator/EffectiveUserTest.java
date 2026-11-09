package io.github.fiftieshousewife.dockerfileinventory.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

class EffectiveUserTest {

    @Test
    void aUserIsRootWhenUnsetRootOrUidZeroNumericWhenAllDigitsAndNamedOtherwiseWhateverItsGroup() {
        assertAll(
                () -> assertThat(new EffectiveUser(null, null).kind()).isEqualTo(EffectiveUser.Kind.ROOT),
                () -> assertThat(new EffectiveUser("", null).kind()).isEqualTo(EffectiveUser.Kind.ROOT),
                () -> assertThat(new EffectiveUser("root", null).kind()).isEqualTo(EffectiveUser.Kind.ROOT),
                () -> assertThat(new EffectiveUser("0:0", null).kind()).isEqualTo(EffectiveUser.Kind.ROOT),
                () -> assertThat(new EffectiveUser("1001:0", null).kind()).isEqualTo(EffectiveUser.Kind.NUMERIC),
                () -> assertThat(new EffectiveUser("nobody", null).kind()).isEqualTo(EffectiveUser.Kind.NAMED));
    }

    @Test
    void ofTwoUsersTheWorseIsRootThenNamedThenNumeric() {
        EffectiveUser root = new EffectiveUser("0", null);
        EffectiveUser named = new EffectiveUser("nobody", null);
        EffectiveUser numeric = new EffectiveUser("1001", null);
        assertAll(
                () -> assertThat(EffectiveUser.worse(numeric, root)).isEqualTo(root),
                () -> assertThat(EffectiveUser.worse(named, numeric)).isEqualTo(named),
                () -> assertThat(EffectiveUser.worse(named, root)).isEqualTo(root));
    }
}
