package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;

/** A {@code RUN} flag; a {@code RUN} keeps its flags in the order written. */
public sealed interface RunFlag {

    /** The flag as it appears in the Dockerfile. */
    String written();

    /** {@code --mount}: its options as written, {@code key=value} or a bare key such as {@code ro}. */
    record Mount(List<String> options) implements RunFlag {

        public Mount {
            options = List.copyOf(Checks.notEmpty(options, "a mount option"));
        }

        public static Mount parse(String spec) {
            return new Mount(List.of(spec.split(",")));
        }

        public String type() {
            return options.stream().filter(o -> o.startsWith("type=")).map(o -> o.substring(5)).findFirst()
                    .orElse("bind");
        }

        public String spec() {
            return String.join(",", options);
        }

        @Override
        public String written() {
            return "--mount=" + spec();
        }
    }

    record Network(String mode) implements RunFlag {

        public Network {
            Checks.notBlank(mode, "a network mode");
        }

        @Override
        public String written() {
            return "--network=" + mode;
        }
    }

    record Security(String mode) implements RunFlag {

        public Security {
            Checks.notBlank(mode, "a security mode");
        }

        @Override
        public String written() {
            return "--security=" + mode;
        }
    }
}
