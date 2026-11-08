package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

// P5: restricted-v2 forbids privilege escalation; restricted (v1) allows it, with SETUID and SETGID dropped.
final class SetuidBits implements PlatformCheck {

    private static final Pattern OCTAL = Pattern.compile("[46][0-7]{3}");
    private static final Pattern SYMBOLIC = Pattern.compile("([ugoa]*)[+=][rwxXt]*s[rwxXst]*");

    @Override
    public Check id() {
        return Check.P5;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        return image.finalStages().stream().flatMap(stage -> stage.permissions().stream())
                .filter(p -> p.command().equals("chmod") && setuid(p.setting()))
                .map(p -> "chmod " + p.setting() + " " + String.join(" ", p.paths()) + " sets a setuid bit, relying "
                        + "on privilege escalation, which restricted-v2 forbids")
                .toList();
    }

    static boolean setuid(String setting) {
        return OCTAL.matcher(setting).matches() || Arrays.stream(setting.split(",")).map(SYMBOLIC::matcher)
                .anyMatch(m -> m.matches() && (m.group(1).isEmpty() || m.group(1).contains("u")
                        || m.group(1).contains("a")));
    }
}
