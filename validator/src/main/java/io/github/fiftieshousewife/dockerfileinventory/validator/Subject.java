package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// One Dockerfile's facts as the checks read them: the parts of a facts.jsonl line that say how its image runs, and
// the user its registry images run as when lineage credits any to it.
record Subject(String blobSha, Facts facts, Config config, List<Stage> stages, List<Step> steps,
               EffectiveUser effectiveUser) {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    record Facts(Base base, List<PackageCall> packageCalls, String finalUser, String userKind,
                 List<String> exposedPorts) {
    }

    record Base(String name, String tag, String digest) {
    }

    record PackageCall(int stage, Call call) {
    }

    record Call(String manager, String subcommand, List<String> packages) {
    }

    record Config(String workdir, List<String> volumes, String healthcheck, Command entrypoint, Command cmd,
                  Map<String, String> environment) {
    }

    record Command(String form, List<String> arguments) {
    }

    record Stage(String name, Base base, String fromStage, List<Copy> copies, List<Permission> permissions) {
    }

    record Copy(String instruction, List<String> sources, String destination, String origin, String chown,
                String chmod) {
    }

    record Step(String keyword, String text) {
    }

    record Permission(String command, String setting, List<String> paths, boolean recursive) {
    }

    Subject {
        stages = List.copyOf(stages);
        steps = List.copyOf(steps);
    }

    Subject(String blobSha, Facts facts, Config config, List<Stage> stages, List<Step> steps) {
        this(blobSha, facts, config, stages, steps, null);
    }

    Subject withEffectiveUser(EffectiveUser user) {
        return new Subject(blobSha, facts, config, stages, steps, user);
    }

    boolean runnable() {
        boolean scratch = facts.base() != null && "scratch".equals(facts.base().name());
        return !scratch || config.entrypoint() != null || config.cmd() != null;
    }

    List<Stage> finalStages() {
        List<Stage> chain = new ArrayList<>();
        Stage stage = stages.isEmpty() ? null : stages.getLast();
        while (stage != null && !chain.contains(stage)) {
            chain.add(stage);
            String from = stage.fromStage();
            stage = from == null ? null : stages.stream().filter(s -> from.equals(s.name())
                    || from.equals(String.valueOf(stages.indexOf(s)))).findFirst().orElse(null);
        }
        return chain;
    }

    static List<Subject> read(Path factsFile) {
        try (var lines = Files.lines(factsFile)) {
            return lines.filter(line -> !line.isBlank()).map(line -> JSON.readValue(line, Subject.class)).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
