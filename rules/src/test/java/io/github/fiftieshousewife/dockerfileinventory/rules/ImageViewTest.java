package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImageViewTest {

    private static final ImageView.Group APP = new ImageView.Group("app", List.of("app-df", "app-df-2"));
    private static final ImageView.Group BASE = new ImageView.Group("base", List.of("base-df"));

    @Test
    void theImagesOwnBlobsComeFromTheFirstImageInItsChain() {
        ImageView image = new ImageView("app", "1001", BASE, List.of(APP, BASE), List.of());

        assertThat(image).satisfies(i -> {
            assertThat(i.ownBlobs()).containsExactly("app-df", "app-df-2");
            assertThat(i.ownBlob()).isEqualTo("app-df");
            assertThat(i.userSetByBlobs()).containsExactly("base-df");
        });
    }

    @Test
    void anImageWithAnEmptyChainOrNoUserSetterHasNoBlobs() {
        ImageView image = new ImageView("app", null, null, List.of(), List.of());

        assertThat(image).satisfies(i -> {
            assertThat(i.ownBlobs()).isEmpty();
            assertThat(i.ownBlob()).isNull();
            assertThat(i.userSetByBlobs()).isEmpty();
        });
    }

    @Test
    void aChainImageWithoutALinkedDockerfileHasNoOwnBlob() {
        ImageView image = new ImageView("app", null, null, List.of(new ImageView.Group("app", List.of())), List.of());

        assertThat(image.ownBlob()).isNull();
    }
}
