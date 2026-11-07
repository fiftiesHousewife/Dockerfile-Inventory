package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Dockerfile;
import io.github.fiftieshousewife.dockerfileinventory.dsl.DockerfileRenderer;
import io.github.fiftieshousewife.dockerfileinventory.dsl.Target;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactExtractor;
import io.github.fiftieshousewife.dockerfileinventory.facts.FactsParser;
import io.github.fiftieshousewife.dockerfileinventory.facts.ParseResult;
import io.github.fiftieshousewife.dockerfileinventory.facts.Steps;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

record RoundTrip(boolean passed, String difference) {

    private static final RoundTrip PASSED = new RoundTrip(true, null);

    static RoundTrip check(String original, Dockerfile model) {
        Docker.File before = parse(original);
        String rendered;
        try {
            rendered = DockerfileRenderer.render(model, before == null ? Target.DEFAULT : targetFor(before));
        } catch (IllegalArgumentException e) {
            return fail("the conversion does not render: " + e.getMessage());
        }
        Docker.File after = parse(rendered);
        if (before == null || after == null) {
            return fail(before == null ? "the original does not parse" : "the rendered conversion does not parse");
        }
        return compare(before, after, model);
    }

    static RoundTrip compare(Docker.File before, Docker.File after, Dockerfile model) {
        if (!Convert.of(after).model().equals(model)) {
            return fail("the rendered conversion converts back to a different model");
        }
        List<String> fromBefore = froms(before);
        List<String> fromAfter = froms(after);
        if (!fromBefore.equals(fromAfter)) {
            return fail("stages differ: " + fromBefore + " became " + fromAfter);
        }
        return stepDifference(CanonicalSteps.of(Steps.of(before)), CanonicalSteps.of(Steps.of(after)))
                .map(RoundTrip::fail)
                .orElse(PASSED);
    }

    static Optional<String> stepDifference(List<String> before, List<String> after) {
        return IntStream.range(0, Math.max(before.size(), after.size()))
                .mapToObj(i -> {
                    String a = i < before.size() ? before.get(i) : "(none)";
                    String b = i < after.size() ? after.get(i) : "(none)";
                    return a.equals(b) ? null : "step " + (i + 1) + " differs: " + a + " became " + b;
                })
                .filter(Objects::nonNull)
                .findFirst();
    }

    static Target targetFor(Docker.File original) {
        List<String> features = FactExtractor.extract(original).buildKitFeatures();
        return new Target(features.contains("heredoc"), features.contains("run-mount"));
    }

    static List<String> froms(Docker.File file) {
        return file.getStages().stream().map(RoundTrip::from).toList();
    }

    static String from(Docker.Stage stage) {
        Docker.From from = stage.getFrom();
        Docker.Argument tag = from.getTag();
        Docker.Argument digest = from.getDigest();
        Docker.From.As as = from.getAs();
        return Values.text(from.getImageName()) + (tag == null ? "" : ":" + Values.text(tag))
                + (digest == null ? "" : "@" + Values.text(digest))
                + (as == null ? "" : " AS " + as.getName().getText());
    }

    private static RoundTrip fail(String difference) {
        return new RoundTrip(false, difference);
    }

    private static Docker.File parse(String text) {
        return new FactsParser().parse("round-trip", text) instanceof ParseResult.Parsed parsed ? parsed.file() : null;
    }
}
