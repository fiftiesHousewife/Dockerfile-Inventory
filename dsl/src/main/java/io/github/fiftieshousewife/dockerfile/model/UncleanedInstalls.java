package io.github.fiftieshousewife.dockerfile.model;

import java.util.Optional;
import java.util.regex.Pattern;

final class UncleanedInstalls {

    private static final Pattern RPM_INSTALL = Pattern.compile("\\b(microdnf|dnf|yum)\\s+(-\\S+\\s+)*install\\b");
    private static final Pattern RPM_CLEAN =
            Pattern.compile("\\b(microdnf|dnf|yum)\\s+(-\\S+\\s+)*clean\\s+all\\b|rm\\s+-rf\\s+/var/cache/(dnf|yum)");
    private static final Pattern APT_INSTALL = Pattern.compile("\\bapt(-get)?\\s+(-\\S+\\s+)*install\\b");
    private static final Pattern APT_CLEAN = Pattern.compile("rm\\s+-rf\\s+/var/lib/apt/lists");
    private static final Pattern APK_ADD = Pattern.compile("\\bapk\\s+add\\b");
    private static final Pattern APK_CLEAN = Pattern.compile("--no-cache|rm\\s+-rf\\s+/var/cache/apk");

    private UncleanedInstalls() {
    }

    static Optional<String> of(Instruction instruction) {
        return runText(instruction).filter(UncleanedInstalls::uncleaned);
    }

    static Optional<String> runText(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Run run when run.body() instanceof RunBody.Shell shell ->
                    Optional.of(String.join(" && ", shell.commands()));
            case Instruction.Run run when run.body() instanceof RunBody.Heredoc heredoc ->
                    Optional.of(String.join("\n", heredoc.lines()));
            case Instruction.Run run when run.body() instanceof RunBody.Exec exec ->
                    Optional.of(String.join(" ", exec.arguments()));
            case Instruction.Raw raw when raw.text().startsWith("RUN ") -> Optional.of(raw.text());
            default -> Optional.empty();
        };
    }

    static boolean uncleaned(String text) {
        return RPM_INSTALL.matcher(text).find() && !RPM_CLEAN.matcher(text).find()
                || APT_INSTALL.matcher(text).find() && !APT_CLEAN.matcher(text).find()
                || APK_ADD.matcher(text).find() && !APK_CLEAN.matcher(text).find();
    }
}
