package io.github.fiftieshousewife.dockerfile.model;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class PolicyCheck {

    private PolicyCheck() {
    }

    static List<PolicyViolation.Breach> breaches(Policy policy, List<Draft> stages) {
        Draft last = stages.getLast();
        BaseImage base = last.baseIn(stages);
        return Stream.of(
                        user(last.lastUser()).stream(),
                        approval(policy, base).stream(),
                        pin(base).stream(),
                        stages.stream().flatMap(stage -> stage.instructions().stream())
                                .flatMap(PolicyCheck::instruction))
                .flatMap(s -> s)
                .toList();
    }

    static Optional<PolicyViolation.Breach> user(String user) {
        if (numericNonRoot(user)) {
            return Optional.empty();
        }
        return Optional.of(new PolicyViolation.Breach("S4", user == null ? "the final stage sets no USER"
                : "the final user " + user + " is not a numeric, non-root UID"));
    }

    static boolean numericNonRoot(String user) {
        if (user == null) {
            return false;
        }
        String uid = user.split(":", 2)[0];
        return !uid.isEmpty() && uid.chars().allMatch(Character::isDigit) && Integer.parseInt(uid) != 0;
    }

    static Optional<PolicyViolation.Breach> approval(Policy policy, BaseImage base) {
        return policy.approves(base) ? Optional.empty() : Optional.of(new PolicyViolation.Breach("S1",
                "base " + base.name() + ":" + base.tag() + " is not on the approved list"));
    }

    static Optional<PolicyViolation.Breach> pin(BaseImage base) {
        if (base.digest() == null) {
            return base.name().equals("scratch") ? Optional.empty()
                    : Optional.of(new PolicyViolation.Breach("R1", "base " + base.name() + " is not pinned by digest"));
        }
        if (base.digest().contains("$")) {
            String argument = base.digest().replaceAll("sha256:\\$\\{?([A-Za-z0-9_]+)}?", "$1");
            return Optional.of(new PolicyViolation.Breach("R1",
                    "base " + base.name() + " is pinned only by a build argument (" + argument + ")"));
        }
        return Optional.empty();
    }

    static Stream<PolicyViolation.Breach> instruction(Instruction instruction) {
        Stream<PolicyViolation.Breach> entrypoint = shellFormEntrypoint(instruction).stream()
                .map(text -> new PolicyViolation.Breach("H2", "shell-form entrypoint: " + text));
        Stream<PolicyViolation.Breach> install = UncleanedInstalls.of(instruction).stream()
                .map(text -> new PolicyViolation.Breach("H1", "an install that does not clean in the same RUN: "
                        + text));
        return Stream.concat(entrypoint, install);
    }

    static Optional<String> shellFormEntrypoint(Instruction instruction) {
        return switch (instruction) {
            case Instruction.Raw raw when raw.text().startsWith("ENTRYPOINT")
                    && !raw.text().substring("ENTRYPOINT".length()).strip().startsWith("[") -> Optional.of(raw.text());
            case Instruction.Entrypoint entrypoint when entrypoint.form() == CommandForm.SHELL ->
                    Optional.of("ENTRYPOINT " + entrypoint.command().getFirst());
            default -> Optional.empty();
        };
    }
}
