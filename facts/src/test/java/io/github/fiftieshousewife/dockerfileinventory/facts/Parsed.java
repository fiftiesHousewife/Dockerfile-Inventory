package io.github.fiftieshousewife.dockerfileinventory.facts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Parsed {

    private Parsed() {
    }

    static Docker.File file(String dockerfile) {
        ParseResult parsed = new FactsParser().parse("sha", dockerfile);
        assertThat(parsed).isInstanceOf(ParseResult.Parsed.class);
        return ((ParseResult.Parsed) parsed).file();
    }

    static Docker.Stage lastStage(String dockerfile) {
        return file(dockerfile).getStages().getLast();
    }

    static List<Docker.Instruction> instructions(String dockerfile) {
        return lastStage(dockerfile).getInstructions();
    }

    static <T extends Docker.Instruction> T first(String dockerfile, Class<T> type) {
        return instructions(dockerfile).stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }
}
