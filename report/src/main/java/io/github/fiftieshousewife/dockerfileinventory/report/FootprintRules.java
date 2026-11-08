package io.github.fiftieshousewife.dockerfileinventory.report;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

record FootprintRules(List<Rule> rules) {

    static final List<String> FOOTPRINTS = List.of("micro", "minimal", "full", "distroless", "scratch", "alpine",
            "slim", Footprint.UNKNOWN);

    record Rule(String image, String tag, String footprint) {
    }

    FootprintRules {
        rules = List.copyOf(rules);
    }

    static FootprintRules of(List<Rule> rules) {
        return new FootprintRules(rules);
    }

    static FootprintRules defaults() {
        try (InputStream in = Objects.requireNonNull(FootprintRules.class.getResourceAsStream("/footprints.csv"))) {
            return parse("footprints.csv", new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FootprintRules read(Path csv) {
        try {
            return parse(csv.toString(), Files.readAllLines(csv));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FootprintRules parse(String source, List<String> lines) {
        return new FootprintRules(IntStream.range(0, lines.size())
                .filter(i -> !lines.get(i).isBlank() && !lines.get(i).startsWith("#")
                        && !lines.get(i).startsWith("image,"))
                .mapToObj(i -> rule(source, i + 1, lines.get(i))).toList());
    }

    static Rule rule(String source, int line, String text) {
        String[] fields = text.split(",", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException(source + " line " + line + ": not image,tag,footprint");
        }
        if (!FOOTPRINTS.contains(fields[2].strip())) {
            throw new IllegalArgumentException(source + " line " + line + ": " + fields[2].strip()
                    + " is not a footprint (" + String.join(", ", FOOTPRINTS) + ")");
        }
        return new Rule(fields[0].strip(), fields[1].strip(), fields[2].strip());
    }

    FootprintRules then(FootprintRules after) {
        List<Rule> all = new ArrayList<>(rules);
        all.addAll(after.rules);
        return new FootprintRules(all);
    }

    String classify(String image) {
        String path = CatalogueLinks.path(image);
        String name = path.substring(path.lastIndexOf('/') + 1);
        String tag = image.matches(".*:[^/]*$") ? image.substring(image.lastIndexOf(':') + 1) : "";
        return rules.stream().filter(rule -> matches(rule, path, name, tag)).map(Rule::footprint).findFirst()
                .orElse(Footprint.UNKNOWN);
    }

    static boolean matches(Rule rule, String path, String name, String tag) {
        return glob(rule.image()).matcher(rule.image().contains("/") ? path : name).matches()
                && (rule.tag().isEmpty() || glob(rule.tag()).matcher(tag).matches());
    }

    static Pattern glob(String pattern) {
        return Pattern.compile(Stream.of(pattern.split("\\*", -1)).map(Pattern::quote)
                .reduce((a, b) -> a + ".*" + b).orElse(""));
    }
}
