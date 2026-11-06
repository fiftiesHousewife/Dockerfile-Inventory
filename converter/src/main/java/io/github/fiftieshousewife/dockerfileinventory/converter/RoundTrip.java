package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.DockerfileRenderer;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Target;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import io.github.fiftieshousewife.dockerfileinventory.facts.Step;
import io.github.fiftieshousewife.dockerfileinventory.facts.Steps;
import java.util.List;
import java.util.stream.Collectors;
import org.openrewrite.docker.tree.Docker;

/**
 * Whether a conversion reproduces its original (Phase 5, D3). It passes when the rendered conversion parses and
 * converts back to the same model, every stage builds {@code FROM} the same thing, and the final image's steps (the
 * normal form lineage matches images on) are the same, with flag and package order inside an install ignored.
 */
public record RoundTrip(boolean passed, String difference) {

    private static final tools.jackson.databind.ObjectMapper JSON = new tools.jackson.databind.ObjectMapper();

    public static RoundTrip check(String original, Dockerfile model) {
        String rendered;
        try {
            rendered = DockerfileRenderer.render(model, Target.DEFAULT);
        } catch (IllegalArgumentException e) {
            return fail("the conversion does not render: " + e.getMessage());
        }
        Docker.File before = parse(original);
        Docker.File after = parse(rendered);
        if (before == null || after == null) {
            return fail(before == null ? "the original does not parse" : "the rendered conversion does not parse");
        }
        if (!Convert.of(after).model().equals(model)) {
            return fail("the rendered conversion converts back to a different model");
        }
        List<String> fromBefore = froms(before);
        List<String> fromAfter = froms(after);
        if (!fromBefore.equals(fromAfter)) {
            return fail("stages differ: " + fromBefore + " became " + fromAfter);
        }
        List<String> stepsBefore = canonical(Steps.of(before));
        List<String> stepsAfter = canonical(Steps.of(after));
        for (int i = 0; i < Math.max(stepsBefore.size(), stepsAfter.size()); i++) {
            String a = i < stepsBefore.size() ? stepsBefore.get(i) : "(none)";
            String b = i < stepsAfter.size() ? stepsAfter.get(i) : "(none)";
            if (!a.equals(b)) {
                return fail("step " + (i + 1) + " differs: " + a + " became " + b);
            }
        }
        return new RoundTrip(true, null);
    }

    private static RoundTrip fail(String difference) {
        return new RoundTrip(false, difference);
    }

    private static Docker.File parse(String text) {
        return new FactsParser().parse("round-trip", text) instanceof ParseResult.Parsed parsed ? parsed.file() : null;
    }

    private static List<String> froms(Docker.File file) {
        return file.getStages().stream().map(stage -> {
            Docker.From from = stage.getFrom();
            Docker.Argument tag = from.getTag();
            Docker.Argument digest = from.getDigest();
            Docker.From.As as = from.getAs();
            return Convert.text(from.getImageName()) + (tag == null ? "" : ":" + Convert.text(tag))
                    + (digest == null ? "" : "@" + Convert.text(digest))
                    + (as == null ? "" : " AS " + as.getName().getText());
        }).toList();
    }

    /**
     * What Docker would not tell apart (D3): an install's flag and package order; a value written in quotes or not;
     * the spacing inside an exec form's JSON array; a key set twice in one {@code ENV} or {@code LABEL}.
     */
    private static List<String> canonical(List<Step> steps) {
        return steps.stream().map(step -> step.keyword() + " " + canonical(step)).toList();
    }

    private static String canonical(Step step) {
        String text = step.text();
        return switch (step.keyword()) {
            case "RUN" -> Installs.of(text).map(i -> i.manager() + " flags=" + sorted(i.flags()) + " packages="
                    + sorted(i.packages())).orElse(json(text));
            case "ENV", "LABEL" -> String.join(" ", new java.util.TreeSet<>(List.of(text.split(" "))));
            case "WORKDIR", "USER" -> Convert.unquote(text);
            default -> json(text);
        };
    }

    /** An exec form's JSON array written without spacing; any other text as it is. */
    private static String json(String text) {
        if (!text.startsWith("[")) {
            return text;
        }
        try {
            return JSON.readTree(text).toString();
        } catch (tools.jackson.core.JacksonException e) {
            return text;
        }
    }

    private static String sorted(List<String> words) {
        return words.stream().sorted().collect(Collectors.joining(","));
    }
}
