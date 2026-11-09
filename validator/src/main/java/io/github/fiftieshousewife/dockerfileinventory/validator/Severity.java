package io.github.fiftieshousewife.dockerfileinventory.validator;

import java.util.Locale;

enum Severity {
    REFUSED, BREAKS, DRIFT, INFORMATION, OFF;

    String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
