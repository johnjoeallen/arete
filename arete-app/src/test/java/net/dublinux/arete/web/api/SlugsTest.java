package net.dublinux.arete.web.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlugsTest {

    @Test
    void validatesShape() {
        assertThat(Slugs.isValid("payments")).isTrue();
        assertThat(Slugs.isValid("payments-ci_2.0")).isTrue();
        assertThat(Slugs.isValid("-leading")).isFalse();
        assertThat(Slugs.isValid("has space")).isFalse();
        assertThat(Slugs.isValid("UPPER")).isFalse();
        assertThat(Slugs.isValid(null)).isFalse();
        assertThat(Slugs.isValid("x".repeat(64))).isFalse();
    }

    @Test
    void slugifyCoercesForTheUi() {
        assertThat(Slugs.slugify("Payments Team")).isEqualTo("payments-team");
        assertThat(Slugs.slugify("  --Foo!!Bar--  ")).isEqualTo("foo-bar");
        assertThat(Slugs.slugify("a".repeat(80))).hasSize(63);
        assertThat(Slugs.slugify("   ")).isNull();
        assertThat(Slugs.slugify("!!!")).isNull();
    }

    @Test
    void requireThrowsOnBadInput() {
        assertThat(Slugs.require(" Payments ", "namespace")).isEqualTo("payments");
        assertThatThrownBy(() -> Slugs.require("bad slug", "namespace"))
                .isInstanceOf(Slugs.SlugException.class);
    }
}
