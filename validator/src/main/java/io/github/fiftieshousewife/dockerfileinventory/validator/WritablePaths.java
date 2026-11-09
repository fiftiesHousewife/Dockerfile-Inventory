package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// P2: the arbitrary UID writes only where group 0 may write; files made during the build are root's, group 0.
final class WritablePaths implements PlatformCheck {

    private static final Pattern OCTAL = Pattern.compile("0?[0-7]{3}");
    private static final Pattern FIX_PERMISSIONS = Pattern.compile("(?:^|[\\s/])fix-permissions\\s+([/$]\\S+)");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{?(\\w+)}?");
    private static final Pattern CLAUSE = Pattern.compile("([ugoa]*)([+=])([rwxXstugo]*)");

    @Override
    public Check id() {
        return Check.P2;
    }

    @Override
    public Certainty certainty() {
        return Certainty.INFERRED;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        List<Subject.Stage> stages = image.finalStages();
        Map<String, String> environment = image.config().environment();
        List<String> fixed = fixedPermissions(image.steps()).stream().map(p -> expanded(p, environment)).toList();
        return written(image).stream()
                .map(path -> new Written(path.kind(), expanded(path.value(), environment)))
                .filter(path -> !path.value().contains("$"))
                .filter(path -> stages.stream().noneMatch(stage -> writable(stage, path.value(), environment)))
                .filter(path -> fixed.stream().noneMatch(directory -> under(path.value(), directory)))
                .map(path -> path.kind() + " " + path.value() + " is never made writable by group 0 (chmod g=u or "
                        + "g+w), so OpenShift's arbitrary UID may not write it")
                .toList();
    }

    record Written(String kind, String value) {
    }

    static List<Written> written(Subject image) {
        Subject.Config config = image.config();
        List<Written> paths = new ArrayList<>();
        config.volumes().forEach(volume -> paths.add(new Written("VOLUME", volume)));
        if (config.workdir() != null && !List.of("/", "/tmp").contains(config.workdir())) {
            paths.add(new Written("WORKDIR", config.workdir()));
        }
        String home = config.environment().get("HOME");
        if (home != null && !List.of("/", "/tmp").contains(home)) {
            paths.add(new Written("HOME", home));
        }
        return paths;
    }

    // sclorg's fix-permissions runs chgrp 0 and chmod g+rw over the whole tree below each path it is given.
    static List<String> fixedPermissions(List<Subject.Step> steps) {
        return steps.stream().filter(step -> step.keyword().equals("RUN"))
                .flatMap(step -> FIX_PERMISSIONS.matcher(step.text()).results().map(m -> m.group(1))).toList();
    }

    static String expanded(String path, Map<String, String> environment) {
        return VARIABLE.matcher(path).replaceAll(m -> Matcher.quoteReplacement(
                environment.getOrDefault(m.group(1), m.group()))).replaceAll("/{2,}", "/");
    }

    static boolean makesGroupWritable(Subject image) {
        return !fixedPermissions(image.steps()).isEmpty() || image.finalStages().stream().anyMatch(stage ->
                stage.permissions().stream().anyMatch(p -> p.command().equals("chmod") && groupWrite(p.setting()))
                        || stage.copies().stream().anyMatch(c -> c.chmod() != null && groupWrite(c.chmod())));
    }

    static boolean writable(Subject.Stage stage, String path, Map<String, String> environment) {
        return stage.permissions().stream().anyMatch(p -> p.command().equals("chmod") && groupWrite(p.setting())
                && covers(p, path, environment))
                || stage.copies().stream().anyMatch(c -> c.chmod() != null && groupWrite(c.chmod())
                && under(path, expanded(c.destination(), environment)));
    }

    static boolean covers(Subject.Permission permission, String path, Map<String, String> environment) {
        return permission.paths().stream().map(p -> expanded(p, environment)).anyMatch(p -> trimmed(p)
                .equals(trimmed(path)) || permission.recursive() && under(path, p));
    }

    static boolean groupWrite(String setting) {
        if (OCTAL.matcher(setting).matches()) {
            return (setting.charAt(setting.length() - 2) - '0' & 2) != 0;
        }
        return Arrays.stream(setting.split(",")).map(CLAUSE::matcher).filter(m -> m.matches())
                .anyMatch(m -> (m.group(1).contains("g") || m.group(1).contains("a"))
                        && (m.group(3).contains("w") || m.group(3).equals("u")));
    }

    static boolean under(String path, String directory) {
        String dir = trimmed(directory);
        return trimmed(path).equals(dir) || path.startsWith(dir.endsWith("/") ? dir : dir + "/");
    }

    private static String trimmed(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
