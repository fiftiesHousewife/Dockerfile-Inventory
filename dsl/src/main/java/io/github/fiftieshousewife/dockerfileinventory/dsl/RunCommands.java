package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

final class RunCommands {

    private RunCommands() {
    }

    static List<String> of(RunBody body) {
        return switch (body) {
            case RunBody.Install install -> PackageInstallRenderer.commands(install.install());
            case RunBody.Shell shell -> shell.commands();
            case RunBody.Exec exec -> throw new IllegalArgumentException("an exec RUN has no commands to join");
            case RunBody.Heredoc heredoc -> throw new IllegalArgumentException("a heredoc RUN has no commands to join");
        };
    }

    static boolean joins(RunBody body) {
        return body instanceof RunBody.Install || body instanceof RunBody.Shell;
    }
}
