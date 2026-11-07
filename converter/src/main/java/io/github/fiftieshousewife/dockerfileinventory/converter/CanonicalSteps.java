package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.facts.Step;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class CanonicalSteps {

    // ${NAME} is $NAME to Docker unless a name character follows: ${A}B is not $AB.
    private static final Pattern BRACES = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}(?![A-Za-z0-9_])");
    private static final ObjectMapper JSON = new ObjectMapper();

    private CanonicalSteps() {
    }

    static List<String> of(List<Step> steps) {
        return steps.stream().map(step -> step.keyword() + " " + canonical(step)).toList();
    }

    static String canonical(Step step) {
        String text = BRACES.matcher(step.text()).replaceAll("\\$$1");
        return switch (step.keyword()) {
            case "RUN" -> Installs.of(text).map(i -> i.manager() + " flags=" + sorted(i.flags()) + " packages="
                    + sorted(i.packages())).orElse(json(text));
            case "ENV", "LABEL" -> String.join(" ", new TreeSet<>(List.of(text.split(" "))));
            case "WORKDIR", "USER" -> Values.unquote(text);
            default -> json(text);
        };
    }

    static String json(String text) {
        if (!text.startsWith("[")) {
            return text;
        }
        try {
            return JSON.readTree(text).toString();
        } catch (JacksonException e) {
            return text;
        }
    }

    private static String sorted(List<String> words) {
        return words.stream().sorted().collect(Collectors.joining(","));
    }
}
