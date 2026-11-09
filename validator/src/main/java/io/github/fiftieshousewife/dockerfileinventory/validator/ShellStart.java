package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.List;
import java.util.Optional;

// OpenShift stops a pod with SIGTERM to PID 1, then SIGKILL after the grace period.
final class ShellStart implements PlatformCheck {

    @Override
    public Check id() {
        return Check.P6;
    }

    @Override
    public Certainty certainty() {
        return Certainty.READ;
    }

    @Override
    public List<String> problems(Subject image, Profile profile) {
        return shellForm(image.config().entrypoint(), image.config().cmd()).stream()
                .map(instruction -> instruction + " is in shell form, so a shell is PID 1 and does not pass on "
                        + "SIGTERM: OpenShift kills the process at the end of the grace period")
                .toList();
    }

    static Optional<String> shellForm(Subject.Command entrypoint, Subject.Command cmd) {
        if (entrypoint != null) {
            return "SHELL".equals(entrypoint.form()) ? Optional.of("ENTRYPOINT") : Optional.empty();
        }
        return cmd != null && "SHELL".equals(cmd.form()) ? Optional.of("CMD") : Optional.empty();
    }
}
