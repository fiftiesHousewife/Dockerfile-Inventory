package io.github.fiftieshousewife.dockerfile.reader;

import io.github.fiftieshousewife.dockerfile.reader.internal.Values;
import io.github.fiftieshousewife.dockerfile.model.Cmd;
import io.github.fiftieshousewife.dockerfile.model.CommandForm;
import io.github.fiftieshousewife.dockerfile.model.Entrypoint;
import io.github.fiftieshousewife.dockerfile.model.Expose;
import io.github.fiftieshousewife.dockerfile.model.Instruction;
import io.github.fiftieshousewife.dockerfile.model.Maintainer;
import io.github.fiftieshousewife.dockerfile.model.Onbuild;
import io.github.fiftieshousewife.dockerfile.model.Port;
import io.github.fiftieshousewife.dockerfile.model.Raw;
import io.github.fiftieshousewife.dockerfile.model.StopSignal;
import io.github.fiftieshousewife.dockerfile.model.User;
import io.github.fiftieshousewife.dockerfile.model.Workdir;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class ConvertInstruction {

    private static final String NOT_JSON = "exec form that is not valid JSON";

    private final Printed printed;

    ConvertInstruction(Printed printed) {
        this.printed = printed;
    }

    Instruction instruction(Docker.Instruction instruction) {
        return switch (instruction) {
            case Docker.Env env -> ConvertPairs.env(printed.text(env));
            case Docker.Label label -> ConvertPairs.label(printed.text(label));
            case Docker.Workdir workdir -> new Workdir(Values.unquote(Values.text(workdir.getPath())));
            case Docker.User user -> user(user);
            case Docker.Expose expose -> expose(expose);
            case Docker.Run run -> ConvertCommands.run(run, printed.text(run));
            case Docker.Entrypoint entrypoint -> entrypoint(entrypoint);
            case Docker.Cmd cmd -> cmd(cmd);
            case Docker.Copy copy -> ConvertCopy.copy(printed.text(copy));
            case Docker.Add add -> ConvertAdd.add(printed.text(add));
            case Docker.Shell shell -> ConvertMetadata.shell(shell, printed.text(shell));
            case Docker.Healthcheck healthcheck -> ConvertMetadata.healthcheck(healthcheck, printed.text(healthcheck));
            case Docker.Onbuild onbuild -> onbuild(onbuild);
            case Docker.Volume volume -> ConvertMetadata.volume(volume, printed.text(volume));
            case Docker.Stopsignal signal -> new StopSignal(Values.text(signal.getSignal()).strip());
            case Docker.Maintainer maintainer -> new Maintainer(Values.text(maintainer.getText()).strip());
            default -> notModelled(printed.text(instruction));
        };
    }

    Instruction expose(Docker.Expose expose) {
        return ports(expose).<Instruction>map(Expose::new)
                .orElseGet(() -> new Raw(printed.text(expose),
                        "EXPOSE with a port that is not a number, range or variable"));
    }

    Instruction entrypoint(Docker.Entrypoint entrypoint) {
        return switch (entrypoint.getCommand()) {
            case Docker.ExecForm exec -> ConvertCommands.arguments(exec).<Instruction>map(Entrypoint::new)
                    .orElseGet(() -> new Raw(printed.text(entrypoint), NOT_JSON));
            case Docker.ShellForm shell -> new Entrypoint(List.of(ConvertCommands.shellText(shell)), CommandForm.SHELL);
            case null, default -> notModelled(printed.text(entrypoint));
        };
    }

    Instruction cmd(Docker.Cmd cmd) {
        return switch (cmd.getCommand()) {
            case Docker.ExecForm exec -> ConvertCommands.arguments(exec).<Instruction>map(Cmd::new)
                    .orElseGet(() -> new Raw(printed.text(cmd), NOT_JSON));
            case Docker.ShellForm shell -> new Cmd(List.of(ConvertCommands.shellText(shell)), CommandForm.SHELL);
            case null, default -> notModelled(printed.text(cmd));
        };
    }

    Instruction onbuild(Docker.Onbuild onbuild) {
        Instruction inner = instruction(onbuild.getInstruction());
        if (inner instanceof Raw raw) {
            return new Raw(printed.text(onbuild), "ONBUILD with " + raw.reason());
        }
        try {
            return new Onbuild(inner);
        } catch (IllegalArgumentException notAllowed) {
            return new Raw(printed.text(onbuild), "ONBUILD with an instruction Docker refuses there");
        }
    }

    static Raw notModelled(String text) {
        String keyword = text.split("\\s", 2)[0].toUpperCase(Locale.ROOT);
        return new Raw(text, keyword + " is not modelled");
    }

    static User user(Docker.User user) {
        Docker.Argument group = user.getGroup();
        return new User(Values.braced(Values.unquote(Values.text(user.getUser())))
                + (group == null ? "" : ":" + Values.braced(Values.unquote(Values.text(group)))));
    }

    static Optional<List<Port>> ports(Docker.Expose expose) {
        try {
            return Optional.of(expose.getPorts().stream().map(port -> new Port(port.getText().strip())).toList());
        } catch (IllegalArgumentException notAPort) {
            return Optional.empty();
        }
    }
}
