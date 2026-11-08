package io.github.fiftieshousewife.dockerfileinventory.facts;

import io.github.fiftieshousewife.dockerfile.reader.internal.ArgPairs;
import io.github.fiftieshousewife.dockerfile.reader.internal.ArgumentText;
import io.github.fiftieshousewife.dockerfile.reader.internal.Expansions;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class StageScan {

    static final Comparator<String> PORT_ORDER =
            Comparator.comparingInt(StageScan::portNumber).thenComparing(Comparator.naturalOrder());

    private final Map<String, String> globalArgs;
    private final Map<String, String> variables = new HashMap<>();
    private final Set<String> ports = new TreeSet<>(PORT_ORDER);
    private String user;
    private EntrypointForm entrypoint;

    StageScan(StageEnd inherited, Map<String, String> globalArgs) {
        this.globalArgs = globalArgs;
        this.user = inherited.user();
        this.entrypoint = inherited.entrypoint();
        this.ports.addAll(inherited.ports());
    }

    Map<String, String> variables() {
        return Collections.unmodifiableMap(variables);
    }

    void accept(Docker.Instruction instruction) {
        switch (instruction) {
            case Docker.Arg arg -> arg(arg);
            case Docker.Env env -> env.getPairs().forEach(pair -> variables.put(pair.getKey().getText(),
                    ArgumentText.of(pair.getValue(), variables)));
            case Docker.User u -> user = ArgumentText.of(u.getUser(), variables);
            case Docker.Entrypoint e -> entrypoint = form(e);
            case Docker.Expose expose -> expose.getPorts()
                    .forEach(port -> ports.add(port.getText().replaceFirst("/tcp$", "")));
            default -> {
            }
        }
    }

    void arg(Docker.Arg arg) {
        ArgPairs.of(arg).forEach((name, declared) -> Optional
                .ofNullable(declared == null ? globalArgs.get(name) : Expansions.expanded(declared, variables))
                .ifPresent(value -> variables.put(name, value)));
    }

    StageEnd result() {
        return new StageEnd(user, List.copyOf(ports), entrypoint);
    }

    static EntrypointForm form(Docker.Entrypoint entrypoint) {
        return entrypoint.getCommand() instanceof Docker.ExecForm ? EntrypointForm.EXEC : EntrypointForm.SHELL;
    }

    static int portNumber(String port) {
        return port.matches("\\d+.*") ? Integer.parseInt(port.replaceFirst("\\D.*", "")) : Integer.MAX_VALUE;
    }
}
