package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.List;
import java.util.Map;

record Command(String name, String summary, List<Setting> settings, boolean arguments, Runner runner) {

    interface Runner {
        int run(List<String> args, Map<String, String> env) throws Exception;
    }

    Command {
        settings = List.copyOf(settings);
    }

    Command(String name, String summary, List<Setting> settings, Stage stage) {
        this(name, summary, settings, false, (args, env) -> {
            stage.run(env);
            return 0;
        });
    }

    interface Stage {
        void run(Map<String, String> env) throws Exception;
    }
}
