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

    /** An install's flags and packages sorted, so their order does not count (D3). */
    private static List<String> canonical(List<Step> steps) {
        return steps.stream().map(step -> step.keyword() + " " + (step.keyword().equals("RUN")
                ? Installs.of(step.text()).map(i -> i.manager() + " flags=" + sorted(i.flags()) + " packages="
                        + sorted(i.packages())).orElse(step.text())
                : step.text())).toList();
    }

    private static String sorted(List<String> words) {
        return words.stream().sorted().collect(Collectors.joining(","));
    }
}
