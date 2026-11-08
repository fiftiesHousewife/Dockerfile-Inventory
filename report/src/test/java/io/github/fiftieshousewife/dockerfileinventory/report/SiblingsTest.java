package io.github.fiftieshousewife.dockerfileinventory.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SiblingsTest {

    private static Group group(List<String> blobShas) {
        return new Group("key", blobShas, blobShas.size(), Set.of(), "ubi");
    }

    @Test
    void aDockerfilesSiblingsAreTheOtherMembersOfItsNearDuplicateGroup() {
        assertThat(Siblings.of(List.of(group(List.of("ccc", "aaa", "bbb")))).get("aaa"))
                .isEqualTo(new Siblings.Found(List.of("bbb", "ccc"), 2));
    }

    @Test
    void aLargeGroupListsTheFirstFiftyAndCountsThemAll() {
        List<String> shas = IntStream.range(0, 60).mapToObj(i -> "s%02d".formatted(i)).toList();

        Siblings.Found found = Siblings.of(List.of(group(shas))).get("s00");

        assertAll(
                () -> assertThat(found.blobShas()).hasSize(50).startsWith("s01"),
                () -> assertThat(found.count()).isEqualTo(59));
    }

    @Test
    void aDockerfileAloneHasNoSiblings() {
        Map<String, Siblings.Found> siblings = Siblings.of(List.of(group(List.of("aaa"))));

        assertThat(Siblings.of(siblings, "aaa")).isEqualTo(new Siblings.Found(List.of(), 0));
    }
}
