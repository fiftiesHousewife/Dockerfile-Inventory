package io.github.fiftieshousewife.dockerfileinventory.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeTest {

    @Test
    void aRegularFileIsABlobButNeitherASymlinkNorATree() {
        Tree.Entry file = new Tree.Entry("Dockerfile", "100644", "blob", "d1");

        assertThat(file).satisfies(
                e -> assertThat(e.isBlob()).isTrue(),
                e -> assertThat(e.isSymlink()).isFalse(),
                e -> assertThat(e.isTree()).isFalse());
    }

    @Test
    void aSymlinkIsABlobWithGitsLinkMode() {
        assertThat(new Tree.Entry("Dockerfile", "120000", "blob", "l1").isSymlink()).isTrue();
    }

    @Test
    void aSubmoduleIsNeitherABlobNorATree() {
        Tree.Entry submodule = new Tree.Entry("vendor/base", "160000", "commit", "s1");

        assertThat(submodule).satisfies(
                e -> assertThat(e.isBlob()).isFalse(),
                e -> assertThat(e.isTree()).isFalse());
    }

    @Test
    void anEntryUnderADirectoryTakesItsPathAsAPrefix() {
        assertThat(new Tree.Entry("Dockerfile", "100644", "blob", "d1").under("svc"))
                .isEqualTo(new Tree.Entry("svc/Dockerfile", "100644", "blob", "d1"));
    }

    @Test
    void keepsItsOwnCopyOfTheEntries() {
        List<Tree.Entry> entries = new ArrayList<>(List.of(new Tree.Entry("a", "100644", "blob", "s")));
        Tree tree = new Tree(false, entries);

        entries.clear();

        assertThat(tree.entries()).hasSize(1);
    }
}
