package io.github.fiftieshousewife.dockerfileinventory.validator;

import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.V2;
import static io.github.fiftieshousewife.dockerfileinventory.validator.Fixtures.subject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BaseRegistryTest {

    private static final Profile ACME = Profile.of(Path.of("src/test/resources/fixtures/copied-profile.json")
            .toString());
    private final BaseRegistry check = new BaseRegistry();

    @Test
    void aBaseFromARegistryOffTheProfilesListIsAProblem() {
        assertThat(check.problems(subject("08bf2be5"), ACME)).containsExactly(
                "the base registry.access.redhat.com/ubi9/ubi-minimal comes from registry.access.redhat.com, which "
                        + "the profile does not allow (registry.acme.example)");
    }

    @Test
    void aProfileWithNoListAllowsEveryRegistry() {
        assertThat(check.problems(subject("08bf2be5"), V2)).isEmpty();
    }

    @Test
    void theRegistryIsTheFirstPartWhenItLooksLikeAHostAndDockerHubOtherwise() {
        assertAll(
                () -> assertThat(BaseRegistry.registry("ubuntu")).isEqualTo("docker.io"),
                () -> assertThat(BaseRegistry.registry("library/ubuntu")).isEqualTo("docker.io"),
                () -> assertThat(BaseRegistry.registry("gcr.io/distroless/static")).isEqualTo("gcr.io"),
                () -> assertThat(BaseRegistry.registry("localhost:5000/app")).isEqualTo("localhost:5000"));
    }
}
