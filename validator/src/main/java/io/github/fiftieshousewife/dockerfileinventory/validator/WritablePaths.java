package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// P2: the arbitrary UID writes only where group 0 may write; files made during the build are root's, group 0.
final class WritablePaths implements PlatformCheck {

    private static final Pattern OCTAL = Pattern.compile("0?[0-7]{3}");
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
        return written(image).stream().filter(path -> !path.value().contains("$"))
                .filter(path -> stages.stream().noneMatch(stage -> writable(stage, path.value())))
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
        if (home != null && !home.equals("/")) {
            paths.add(new Written("HOME", home));
        }
        return paths;
    }

    static boolean writable(Subject.Stage stage, String path) {
        return stage.permissions().stream().anyMatch(p -> p.command().equals("chmod") && groupWrite(p.setting())
                && covers(p, path))
                || stage.copies().stream().anyMatch(c -> c.chmod() != null && groupWrite(c.chmod())
                && under(path, c.destination()));
    }

    static boolean covers(Subject.Permission permission, String path) {
        return permission.paths().stream().anyMatch(p -> trimmed(p).equals(trimmed(path))
                || permission.recursive() && under(path, p));
    }

    static boolean groupWrite(String setting) {
        if (OCTAL.matcher(setting).matches()) {
            return (setting.charAt(setting.length() - 2) - '0' & 2) != 0;
        }
        return Arrays.stream(setting.split(",")).map(CLAUSE::matcher).filter(m -> m.matches())
                .anyMatch(m -> (m.group(1).contains("g") || m.group(1).contains("a"))
                        && (m.group(3).contains("w") || m.group(3).equals("u")));
    }

    private static boolean under(String path, String directory) {
        String dir = trimmed(directory);
        return trimmed(path).equals(dir) || path.startsWith(dir.endsWith("/") ? dir : dir + "/");
    }

    private static String trimmed(String path) {
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
