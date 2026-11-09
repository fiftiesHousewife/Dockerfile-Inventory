package io.github.fiftieshousewife.dockerfileinventory.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.openrewrite.Cursor;

class FinalImageTest {

    @Test
    void theFinalImageIncludesEachEarlierStageItIsBuiltFromByName() {
        Docker.File file = Subjects.of("""
                FROM ubi9 AS base
                USER 1
                FROM golang AS build
                RUN make
                FROM base
                USER 2
                """).file();

        Cursor cursor = new Cursor(null, file);

        assertThat(FinalImage.instructions(file)).extracting(i -> i.printTrimmed(cursor))
                .containsExactly("USER 1", "USER 2");
    }

    @Test
    void earlierStageFindsTheNearestEarlierStageOfThatNameAndNoneForATaggedImage() {
        List<Docker.Stage> stages = Subjects.of("FROM a AS x\nFROM b AS x\nFROM x\nFROM x:1\n").file().getStages();

        assertAll(
                () -> assertThat(FinalImage.earlierStage(stages.get(2).getFrom(), stages.subList(0, 2))).isEqualTo(1),
                () -> assertThat(FinalImage.earlierStage(stages.get(3).getFrom(), stages.subList(0, 3)))
                        .isEqualTo(-1),
                () -> assertThat(FinalImage.earlierStage(stages.get(0).getFrom(), List.of())).isEqualTo(-1));
    }

    @Test
    void namedIsAStageWhoseAsNameMatches() {
        List<Docker.Stage> stages = Subjects.of("FROM a AS build\nFROM b\n").file().getStages();

        assertAll(
                () -> assertThat(FinalImage.named(stages.get(0), "build")).isTrue(),
                () -> assertThat(FinalImage.named(stages.get(0), "other")).isFalse(),
                () -> assertThat(FinalImage.named(stages.get(1), "build")).isFalse());
    }

    @Test
    void theChainRunsFromTheLastStageBackThroughEachStageItIsBuiltFrom() {
        List<Docker.Stage> stages = Subjects.of("FROM a AS base\nFROM b AS other\nFROM base\n").file().getStages();

        assertThat(FinalImage.chain(stages)).containsExactly(2, 0);
    }
}
