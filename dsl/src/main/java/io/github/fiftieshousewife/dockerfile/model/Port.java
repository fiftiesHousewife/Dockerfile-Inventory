package io.github.fiftieshousewife.dockerfile.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One {@code EXPOSE} spec: a port or a range, or a variable, with an optional {@code /tcp}, {@code /udp} or
 * {@code /sctp}. */
public record Port(String spec) {

    private static final Pattern SPEC = Pattern.compile(
            "(?:(\\d{1,5})(?:-(\\d{1,5}))?|\\$[A-Za-z_]\\w*|\\$\\{[A-Za-z_]\\w*})(?:/(?:tcp|udp|sctp))?");

    public Port {
        Matcher matcher = SPEC.matcher(spec);
        if (!matcher.matches() || !inRange(matcher.group(1), matcher.group(2))) {
            throw new IllegalArgumentException("not a port: '" + spec + "'");
        }
    }

    public static Port of(int port) {
        return new Port(Integer.toString(port));
    }

    static boolean inRange(String low, String high) {
        if (low == null) {
            return true;
        }
        int from = Integer.parseInt(low);
        int to = high == null ? from : Integer.parseInt(high);
        return from >= 1 && to <= 65535 && from <= to;
    }
}
