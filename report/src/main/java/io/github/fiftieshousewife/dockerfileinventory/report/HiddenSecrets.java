package io.github.fiftieshousewife.dockerfileinventory.report;

import io.github.fiftieshousewife.dockerfileinventory.dsl.Secrets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HiddenSecrets {

    private static final Pattern PAIRS_INSTRUCTION = Pattern.compile("(?i)\\s*(ENV|ARG|LABEL)\\s");
    private static final Pattern LEGACY_PAIR =
            Pattern.compile("(?i)(\\s*(?:ENV|LABEL)\\s+([A-Za-z_][\\w.-]*)\\s+)[^=\\s].*");
    private static final Pattern PAIR =
            Pattern.compile("([A-Za-z_][\\w.-]*)=(\"(?:[^\"\\\\]|\\\\.)*\"|'[^']*'|[^\\s\\\\]+)");

    private HiddenSecrets() {
    }

    static String hidden(String dockerfile) {
        StringBuilder out = new StringBuilder();
        boolean pairs = false;
        for (String line : dockerfile.split("\n", -1)) {
            boolean starts = PAIRS_INSTRUCTION.matcher(line).lookingAt();
            if (starts || pairs) {
                line = hidePairs(line, starts);
            }
            pairs = (starts || pairs) && line.stripTrailing().endsWith("\\");
            out.append(Secrets.hideShapes(line)).append('\n');
        }
        return out.substring(0, out.length() - 1);
    }

    static String hidePairs(String line, boolean starts) {
        Matcher legacy = LEGACY_PAIR.matcher(line);
        if (starts && legacy.matches()) {
            return Secrets.secret(legacy.group(2), line.substring(legacy.end(1))) ? legacy.group(1) + "(hidden)" : line;
        }
        return PAIR.matcher(line).replaceAll(m -> Matcher.quoteReplacement(
                Secrets.secret(m.group(1), m.group(2)) ? m.group(1) + "=(hidden)" : m.group()));
    }
}
