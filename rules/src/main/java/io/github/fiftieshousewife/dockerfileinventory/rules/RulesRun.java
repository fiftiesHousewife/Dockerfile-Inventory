package io.github.fiftieshousewife.dockerfileinventory.rules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.ObjectMapper;

final class RulesRun {

    private final Path discoveryDir;
    private final Path lineageDir;
    private final Path allowList;
    private final Path eolList;
    private final Path outDir;
    private final ObjectMapper json = new ObjectMapper();

    RulesRun(Path discoveryDir, Path lineageDir, Path allowList, Path eolList, Path outDir) {
        this.discoveryDir = discoveryDir;
        this.lineageDir = lineageDir;
        this.allowList = allowList;
        this.eolList = eolList;
        this.outDir = outDir;
    }

    void run() {
        RuleSet rules = RuleSet.of(list(allowList), list(eolList), lineageDir != null);
        DiscoveryFiles discovery = new DiscoveryFiles(discoveryDir);
        List<Finding> perFile = new RuleRun(rules.perFile()).run(discovery.subjects());
        List<ImageView> images = lineageDir == null ? List.of() : LineageView.read(lineageDir);
        List<Finding> findings = Stream.concat(perFile.stream(),
                effective(rules.effective(), images, discovery::text).stream()).toList();
        write(findings, discovery.locations(), rules.skipped());
    }

    static BaseList list(Path csv) {
        return csv == null ? null : BaseList.read(csv);
    }

    static List<Finding> effective(List<EffectiveRule> rules, List<ImageView> images,
                                   Function<String, String> blobText) {
        return images.stream()
                .flatMap(image -> rules.stream().flatMap(rule -> rule.check(image, blobText).stream()))
                .toList();
    }

    String jsonLines(List<Finding> findings) {
        return findings.stream().map(json::writeValueAsString).collect(Collectors.joining("\n"))
                + (findings.isEmpty() ? "" : "\n");
    }

    private void write(List<Finding> findings, Map<String, List<Location>> locations, List<String> skipped) {
        write("findings.jsonl", jsonLines(findings));
        write("rates.json", json.writerWithDefaultPrettyPrinter()
                .writeValueAsString(RuleRates.of(findings, locations, skipped)));
        Samples.of(findings, locations).forEach((rule, csv) -> write("samples/" + rule + ".csv", csv));
    }

    private void write(String file, String text) {
        try {
            Files.createDirectories(outDir.resolve("samples"));
            Files.writeString(outDir.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
