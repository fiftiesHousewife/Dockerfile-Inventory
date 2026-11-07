package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.Map;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class ArgumentText {

    private ArgumentText() {
    }

    static String of(Docker.Argument argument, Map<String, String> variables) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText()
                        : variable((Docker.EnvironmentVariable) c, variables))
                .collect(Collectors.joining());
    }

    private static String variable(Docker.EnvironmentVariable variable, Map<String, String> variables) {
        return variables.getOrDefault(variable.getName(), "${" + variable.getName() + "}");
    }
}
