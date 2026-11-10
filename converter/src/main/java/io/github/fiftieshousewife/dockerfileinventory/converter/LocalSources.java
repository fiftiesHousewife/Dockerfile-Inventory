package io.github.fiftieshousewife.dockerfileinventory.converter;

import io.github.fiftieshousewife.dockerfile.model.Add;
import io.github.fiftieshousewife.dockerfile.model.Arg;
import io.github.fiftieshousewife.dockerfile.model.Copy;
import io.github.fiftieshousewife.dockerfile.model.Run;
import io.github.fiftieshousewife.dockerfile.model.Dockerfile;
import io.github.fiftieshousewife.dockerfile.model.Env;
import io.github.fiftieshousewife.dockerfile.model.RunFlag;
import io.github.fiftieshousewife.dockerfile.model.Stage;
import io.github.fiftieshousewife.dockerfile.reader.internal.Expansions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class LocalSources {

    private static final Pattern DEFAULTED = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*):?-([^}]*)}");
    private static final Set<String> PLATFORM = Set.of("TARGETPLATFORM", "TARGETOS", "TARGETARCH", "TARGETVARIANT",
            "BUILDPLATFORM", "BUILDOS", "BUILDARCH", "BUILDVARIANT");

    private LocalSources() {
    }

    static List<String> of(Dockerfile model) {
        return model.stages().stream().flatMap(LocalSources::sources).toList();
    }

    static Stream<String> sources(Stage stage) {
        Map<String, String> variables = new HashMap<>();
        return stage.instructions().stream().<String>mapMulti((instruction, sources) -> {
            switch (instruction) {
                case Arg arg when arg.defaultValue() != null ->
                        variables.put(arg.name(), value(arg.defaultValue(), variables));
                case Env env -> env.values().forEach((name, text) -> variables.put(name, value(text, variables)));
                case Copy copy when copy.fromStage() == null ->
                        copy.sources().forEach(source -> sources.accept(value(source, variables)));
                case Add add -> add.sources().stream().filter(source -> !remote(source))
                        .forEach(source -> sources.accept(value(source, variables)));
                default -> {
                }
            }
        });
    }

    static String value(String text, Map<String, String> variables) {
        String defaulted = DEFAULTED.matcher(text).replaceAll(reference -> Matcher.quoteReplacement(
                defaulted(reference.group(1), reference.group(2), variables)));
        return Expansions.expanded(defaulted, variables);
    }

    // The builder sets the platform's arguments, so a default given for one never applies; Stubs expands them.
    static String defaulted(String name, String fallback, Map<String, String> variables) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        return PLATFORM.contains(name) ? "${" + name + "}" : fallback;
    }

    static List<String> secretIds(Dockerfile model) {
        return model.stages().stream()
                .flatMap(stage -> stage.instructions().stream())
                .<RunFlag>mapMulti((instruction, flags) -> {
                    if (instruction instanceof Run run) {
                        run.flags().forEach(flags);
                    }
                })
                .<String>mapMulti((flag, ids) -> {
                    if (flag instanceof RunFlag.Mount mount && mount.type().equals("secret")) {
                        mount.options().stream().filter(option -> option.startsWith("id="))
                                .forEach(option -> ids.accept(option.substring(3)));
                    }
                })
                .distinct()
                .toList();
    }

    static boolean remote(String source) {
        return source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@");
    }
}
