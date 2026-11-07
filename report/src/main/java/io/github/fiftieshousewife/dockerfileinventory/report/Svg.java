package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Locale;

final class Svg {

    static final int WIDTH = 640;

    private Svg() {
    }

    static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
