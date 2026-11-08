package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// P4: neither restricted SCC lets a process switch user; v2 forbids escalation and both drop SETUID and SETGID.
final class UserSwitch implements PlatformCheck {

    private static final Pattern SWITCH = Pattern.compile("(?:^|[\\s;&|/])(sudo|su|gosu|su-exec)(?=\\s|$)");

    @Override
    public Check id() {
        return Check.P4;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        List<String> problems = new ArrayList<>();
        Stream.of(image.config().entrypoint(), image.config().cmd()).filter(c -> c != null)
                .flatMap(c -> switching(c.arguments()).stream()).distinct()
                .forEach(tool -> problems.add("the start command runs " + tool + ", which OpenShift's arbitrary UID "
                        + "cannot use to switch user"));
        if (sudoInFinalImage(image)) {
            problems.add("sudo is installed in the final image; OpenShift's arbitrary UID cannot use it to switch "
                    + "user or raise privilege");
        }
        return problems;
    }

    static Optional<String> switching(List<String> arguments) {
        Matcher matcher = SWITCH.matcher(String.join(" ", arguments));
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    static boolean sudoInFinalImage(Subject image) {
        Set<Integer> finals = new HashSet<>();
        image.finalStages().forEach(stage -> finals.add(image.stages().indexOf(stage)));
        return image.facts().packageCalls().stream()
                .anyMatch(p -> finals.contains(p.stage()) && p.call().packages().contains("sudo"));
    }
}
