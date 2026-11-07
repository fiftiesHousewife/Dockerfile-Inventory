package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.regex.Pattern;
import java.util.stream.Collectors;
import io.github.fiftieshousewife.dockerfile.parser.tree.Docker;

final class Values {

    private static final Pattern BARE_VARIABLE = Pattern.compile("\\$([A-Za-z_][A-Za-z0-9_]*)");

    private Values() {
    }

    static String braced(String value) {
        return BARE_VARIABLE.matcher(value).replaceAll("\\${$1}");
    }

    static String text(Docker.Argument argument) {
        return argument.getContents().stream()
                .map(c -> c instanceof Docker.Literal l ? l.getText()
                        : "${" + ((Docker.EnvironmentVariable) c).getName() + "}")
                .collect(Collectors.joining());
    }

    static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
