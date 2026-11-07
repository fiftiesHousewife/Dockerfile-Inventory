package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

final class RunCommands {

    private RunCommands() {
    }

    static List<String> of(RunBody body) {
        return switch (body) {
            case RunBody.Install install -> PackageInstallRenderer.commands(install.install());
            case RunBody.Shell shell -> shell.commands();
        };
    }
}
