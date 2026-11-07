package io.github.fiftieshousewife.dockerfileinventory.facts;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class RunScripts {

    private static final Set<String> OTHER_SHELLS = Set.of("powershell", "pwsh", "cmd");

    private RunScripts() {
    }

    static Optional<String> script(Docker.Run run) {
        return switch (run.getCommand()) {
            case Docker.ShellForm shell -> Optional.of(Continuations.withoutCommentLines(shell.getArgument().getText()));
            case Docker.HeredocForm heredoc -> Optional.of(heredoc.getBodies().stream()
                    .flatMap(b -> b.getContentLines().stream())
                    .collect(Collectors.joining("\n")));
            default -> Optional.empty();
        };
    }

    static Optional<List<List<String>>> commands(Docker.Run run) {
        return run.getCommand() instanceof Docker.ExecForm exec
                ? Optional.of(List.of(exec.getArguments().stream().map(Docker.Literal::getText).toList()))
                : script(run).flatMap(ShellTokeniser::parse);
    }

    static boolean otherShell(Docker.Shell shell) {
        return !shell.getArguments().isEmpty()
                && isOtherShell(ArgumentText.of(shell.getArguments().getFirst(), Map.of()));
    }

    static boolean isOtherShell(String program) {
        String plain = program.replace("\"", "").replace("'", "").strip().toLowerCase(Locale.ROOT);
        String name = plain.substring(Math.max(plain.lastIndexOf('/'), plain.lastIndexOf('\\')) + 1);
        return OTHER_SHELLS.contains(name.replaceFirst("\\.exe$", ""));
    }
}
