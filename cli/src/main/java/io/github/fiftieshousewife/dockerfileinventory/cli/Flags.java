package io.github.fiftieshousewife.dockerfileinventory.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The flags any command takes, wherever they stand; the rest are the command's own, in order.
record Flags(List<String> rest, Optional<String> settings, Optional<String> load) {

    static final Map<String, String> VALUES = Map.of("--settings", "a YAML file", "--load", "gentle or normal");

    Flags {
        rest = List.copyOf(rest);
    }

    static Flags of(List<String> args) {
        List<String> rest = new ArrayList<>();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!VALUES.containsKey(arg)) {
                rest.add(arg);
            } else if (i + 1 < args.size()) {
                values.put(arg, args.get(++i));
            } else {
                throw new UsageError(arg + " needs a value: " + VALUES.get(arg));
            }
        }
        return new Flags(rest, Optional.ofNullable(values.get("--settings")),
                Optional.ofNullable(values.get("--load")));
    }
}
