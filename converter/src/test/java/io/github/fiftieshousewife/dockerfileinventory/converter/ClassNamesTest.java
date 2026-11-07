package io.github.fiftieshousewife.dockerfileinventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClassNamesTest {

    @Test
    void theClassNameIsRepositoryFoldersAndSuffixInCamelCase() {
        assertThat(ClassNames.className(new Location("payments/api", "Dockerfile"))).isEqualTo("PaymentsApiImage");
        assertThat(ClassNames.className(new Location("platform/build-tools", "ci/images/Dockerfile.rhel-9")))
                .isEqualTo("PlatformBuildToolsCiImagesRhel9Image");
    }

    @Test
    void aClassNameThatWouldStartWithADigitStartsWithImage() {
        assertThat(ClassNames.className(new Location("3scale/apicast", "Dockerfile")))
                .isEqualTo("Image3scaleApicastImage");
    }

    @Test
    void thePackageIsTheOwnerInLowerCaseWithOtherCharactersAsUnderscores() {
        assertThat(ClassNames.packageName(new Location("Red-Hat.IO/api", "Dockerfile"))).isEqualTo("images.red_hat_io");
    }
}
