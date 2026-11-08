package io.github.fiftieshousewife.dockerfile.model;

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

        /** A mount as Docker writes it after {@code --mount=}, such as {@code type=cache,target=/root/.m2}. */
        public static Mount parse(String spec) {
            return new Mount(List.of(spec.split(",")));
        }

        /** The mount type; Docker's default is {@code bind}. */
        public String type() {
            return options.stream().filter(o -> o.startsWith("type=")).map(o -> o.substring(5)).findFirst()
                    .orElse("bind");
        }

        /** The options as written after {@code --mount=}. */
        public String spec() {
            return String.join(",", options);
        }

        @Override
        public String written() {
            return "--mount=" + spec();
        }
    }

    /** {@code --network}: the network the step runs with, such as {@code none}. */
    record Network(String mode) implements RunFlag {

        public Network {
            Checks.notBlank(mode, "a network mode");
        }

        @Override
        public String written() {
            return "--network=" + mode;
        }
    }

    /** {@code --security}: {@code insecure} or {@code sandbox}. */
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
